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

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class TrackerPeerSourceFactory implements PeerSourceFactory {

    private ITrackerService trackerService;
    private TorrentRegistry torrentRegistry;
    private Duration trackerQueryInterval;
    private ConcurrentMap<TorrentId, ConcurrentMap<AnnounceKey, TrackerPeerSource>> peerSources;

    private ExecutorService executor;

    public TrackerPeerSourceFactory(ITrackerService trackerService,
                                    TorrentRegistry torrentRegistry,
                                    IRuntimeLifecycleBinder lifecycleBinder,
                                    Duration trackerQueryInterval) {
        this.trackerService = trackerService;
        this.torrentRegistry = torrentRegistry;
        this.trackerQueryInterval = trackerQueryInterval;
        this.peerSources = new ConcurrentHashMap<>();

        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "bt.peer.tracker-peer-source-" + i.incrementAndGet());
            }
        });
        lifecycleBinder.onShutdown("Shutdown tracker peer sources", executor::shutdownNow);
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);
        if (!torrentOptional.isPresent()) {
            // return a mock peer source instead of failing, because torrent might be being fetched at the time
            return noopSource;
        }

        Torrent torrent = torrentOptional.get();
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
        if (!announceKey.isPresent()) {
            throw new IllegalStateException("Torrent does not have an announce key");
        }

        return getOrCreateTrackerPeerSource(torrentId, announceKey.get());
    }

    /**
     * @since 1.3
     */
    public PeerSource getPeerSource(TorrentId torrentId, AnnounceKey announceKey) {
        return getOrCreateTrackerPeerSource(torrentId, announceKey);
    }

    private TrackerPeerSource getOrCreateTrackerPeerSource(TorrentId torrentId, AnnounceKey announceKey) {
        ConcurrentMap<AnnounceKey, TrackerPeerSource> map = getOrCreateTrackerPeerSourcesMap(torrentId);
        TrackerPeerSource trackerPeerSource = map.get(announceKey);
        if (trackerPeerSource == null) {
            trackerPeerSource = createTrackerPeerSource(torrentId, announceKey);
            TrackerPeerSource existing = map.putIfAbsent(announceKey, trackerPeerSource);
            if (existing != null) {
                trackerPeerSource = existing;
            }
        }
        return trackerPeerSource;
    }

    private ConcurrentMap<AnnounceKey, TrackerPeerSource> getOrCreateTrackerPeerSourcesMap(TorrentId torrentId) {
        ConcurrentMap<AnnounceKey, TrackerPeerSource> map = peerSources.get(torrentId);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            ConcurrentMap<AnnounceKey, TrackerPeerSource> existing = peerSources.putIfAbsent(torrentId, map);
            if (existing != null) {
                map = existing;
            }
        }
        return map;
    }

    private TrackerPeerSource createTrackerPeerSource(TorrentId torrentId, AnnounceKey announceKey) {
        return new TrackerPeerSource(executor, trackerService.getTracker(announceKey), torrentId, trackerQueryInterval);
    }

    private static final PeerSource noopSource = new PeerSource() {
        @Override
        public boolean update() {
            return false;
        }

        @Override
        public Collection<Peer> getPeers() {
            return Collections.emptyList();
        }
    };
}
