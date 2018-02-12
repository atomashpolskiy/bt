/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.peer;

import bt.event.EventSink;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerRegistry implements IPeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRegistry.class);

    private final Peer localPeer;
    private final IPeerCache cache;

    private TorrentRegistry torrentRegistry;
    private ITrackerService trackerService;
    private EventSink eventSink;
    private TrackerPeerSourceFactory trackerPeerSourceFactory;
    private Set<PeerSourceFactory> extraPeerSourceFactories;

    private ConcurrentMap<TorrentId, Set<AnnounceKey>> extraAnnounceKeys;
    private ReentrantLock extraAnnounceKeysLock;

    @Inject
    public PeerRegistry(IRuntimeLifecycleBinder lifecycleBinder,
                        IdentityService idService,
                        TorrentRegistry torrentRegistry,
                        ITrackerService trackerService,
                        EventSink eventSink,
                        IPeerCache cache,
                        Set<PeerSourceFactory> extraPeerSourceFactories,
                        Config config) {

        this.localPeer = new InetPeer(config.getAcceptorAddress(), config.getAcceptorPort(), idService.getLocalPeerId());
        this.cache = cache;

        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
        this.eventSink = eventSink;
        this.trackerPeerSourceFactory = new TrackerPeerSourceFactory(trackerService, torrentRegistry,
                lifecycleBinder, config.getTrackerQueryInterval());
        this.extraPeerSourceFactories = extraPeerSourceFactories;

        this.extraAnnounceKeys = new ConcurrentHashMap<>();
        this.extraAnnounceKeysLock = new ReentrantLock();

        createExecutor(lifecycleBinder, config.getPeerDiscoveryInterval());
    }

    private void createExecutor(IRuntimeLifecycleBinder lifecycleBinder, Duration peerDiscoveryInterval) {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peer.peer-collector"));
        lifecycleBinder.onStartup("Schedule periodic peer lookup", () -> executor.scheduleAtFixedRate(
                this::collectAndVisitPeers, 1, peerDiscoveryInterval.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown("Shutdown peer lookup scheduler", executor::shutdownNow);
    }

    private void collectAndVisitPeers() {
        torrentRegistry.getTorrentIds().forEach(torrentId -> {
            Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
            if (descriptor.isPresent() && descriptor.get().isActive()) {
                Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);

                Optional<AnnounceKey> torrentAnnounceKey = torrentOptional.isPresent() ?
                        torrentOptional.get().getAnnounceKey() : Optional.empty();

                Collection<AnnounceKey> extraTorrentAnnounceKeys = extraAnnounceKeys.get(torrentId);
                if (extraTorrentAnnounceKeys == null) {
                    queryTrackers(torrentId, torrentAnnounceKey, Collections.emptyList());
                } else if (torrentOptional.isPresent() && torrentOptional.get().isPrivate()) {
                    if (extraTorrentAnnounceKeys.size() > 0) {
                        // prevent violating private torrents' rule of "only one tracker"
                        LOGGER.warn("Will not query extra trackers for a private torrent, id: {}", torrentId);
                    }
                } else {
                    // more announce keys might be added at the same time;
                    // querying all trackers can be time-consuming, so we make a copy of the collection
                    // to prevent blocking callers of addPeerSource(TorrentId, AnnounceKey) for too long
                    Collection<AnnounceKey> extraTorrentAnnounceKeysCopy;
                    extraAnnounceKeysLock.lock();
                    try {
                        extraTorrentAnnounceKeysCopy = new ArrayList<>(extraTorrentAnnounceKeys);
                    } finally {
                        extraAnnounceKeysLock.unlock();
                    }
                    queryTrackers(torrentId, torrentAnnounceKey, extraTorrentAnnounceKeysCopy);
                }

                // disallow querying peer sources other than the tracker for private torrents
                if ((!torrentOptional.isPresent() || !torrentOptional.get().isPrivate()) && !extraPeerSourceFactories.isEmpty()) {
                    extraPeerSourceFactories.forEach(factory ->
                            queryPeerSource(torrentId, factory.getPeerSource(torrentId)));
                }
            }
        });
    }

    private void queryTrackers(TorrentId torrentId, Optional<AnnounceKey> torrentAnnounceKey, Collection<AnnounceKey> extraAnnounceKeys) {
        torrentAnnounceKey.ifPresent(announceKey -> {
            try {
                queryTracker(torrentId, announceKey);
            } catch (Exception e) {
                LOGGER.error("Error when querying tracker (torrent's announce key): " + announceKey, e);
            }
        });
        extraAnnounceKeys.forEach(announceKey -> {
            try {
                queryTracker(torrentId, announceKey);
            } catch (Exception e) {
                LOGGER.error("Error when querying tracker (extra announce key): " + announceKey, e);
            }
        });
    }

    private void queryTracker(TorrentId torrentId, AnnounceKey announceKey) {
        if (mightCreateTracker(announceKey)) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Querying tracker peer source (announce key: {}) for torrent id: {}", announceKey, torrentId);
            }
            queryPeerSource(torrentId, trackerPeerSourceFactory.getPeerSource(torrentId, announceKey));
        }
    }

    private boolean mightCreateTracker(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            // TODO: need some more sophisticated solution because some of the trackers might be supported
            for (List<String> tier : announceKey.getTrackerUrls()) {
                for (String trackerUrl : tier) {
                    if (!trackerService.isSupportedProtocol(trackerUrl)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return trackerService.isSupportedProtocol(announceKey.getTrackerUrl());
        }
    }

    private void queryPeerSource(TorrentId torrentId, PeerSource peerSource) {
        try {
            if (peerSource.update()) {
                Collection<Peer> discoveredPeers = peerSource.getPeers();
                Set<Peer> addedPeers = new HashSet<>();
                Iterator<Peer> iter = discoveredPeers.iterator();
                while (iter.hasNext()) {
                    Peer peer = iter.next();
                    if (!addedPeers.contains(peer)) {
                        addPeer(torrentId, peer);
                        addedPeers.add(peer);
                    }
                    iter.remove();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error when querying peer source: " + peerSource, e);
        }
    }

    @Override
    public void addPeer(TorrentId torrentId, Peer peer) {
        if (isLocal(peer)) {
            return;
        }
        cache.store(peer);
        eventSink.firePeerDiscovered(torrentId, peer);
    }

    @Override
    public void addPeerSource(TorrentId torrentId, AnnounceKey announceKey) {
        extraAnnounceKeysLock.lock();
        try {
            getOrCreateExtraAnnounceKeys(torrentId).add(announceKey);
        } finally {
            extraAnnounceKeysLock.unlock();
        }
    }

    private Set<AnnounceKey> getOrCreateExtraAnnounceKeys(TorrentId torrentId) {
        Set<AnnounceKey> announceKeys = extraAnnounceKeys.get(torrentId);
        if (announceKeys == null) {
            announceKeys = ConcurrentHashMap.newKeySet();
            Set<AnnounceKey> existing = extraAnnounceKeys.putIfAbsent(torrentId, announceKeys);
            if (existing != null) {
                announceKeys = existing;
            }
        }
        return announceKeys;
    }

    private boolean isLocal(Peer peer) {
        return peer.getInetAddress().isAnyLocalAddress() && localPeer.getPort() == peer.getPort();
    }

    @Override
    public Peer getLocalPeer() {
        return localPeer;
    }
}
