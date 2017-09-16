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

package bt.it.fixture;

import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.peer.IPeerRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Provides tracker, that is shared among all peers in the swarm.
 *
 * @since 1.0
 */
public class SharedTrackerModule implements Module {

    /**
     * Allows to filter the list of peers, that is returned by shared tracker to any given peer.
     *
     * @since 1.0
     */
    public interface PeerFilter {

        /**
         * Filter the list of peers, that will be returned to any given peer.
         *
         * @param self Some of the peers in the swarm
         * @param peers All peers in the swarm, that are known to the shared tracker
         * @return Filtered list of peers
         * @since 1.0
         */
        Collection<Peer> filterPeers(Peer self, Set<Peer> peers);
    }

    private PeerFilter peerFilter;

    /**
     * Create a shared tracker with default peer filter
     * (return the list of all peers except for the requesting peer).
     *
     * @since 1.0
     */
    public SharedTrackerModule() {
        this(new DefaultPeerFilter());
    }

    /**
     * Create a shared tracker with custom peer filter.
     *
     * @param peerFilter Custom peer filter
     * @since 1.0
     */
    public SharedTrackerModule(PeerFilter peerFilter) {
        this.peerFilter = peerFilter;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(ITrackerService.class).to(PeerTrackerService.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public PeerFilter providePeerFilter() {
        return peerFilter;
    }

    private static class DefaultPeerFilter implements PeerFilter {

        @Override
        public Collection<Peer> filterPeers(Peer self, Set<Peer> peers) {
            peers.remove(self);
            return peers;
        }
    }

    private static class PeerTrackerService implements ITrackerService {

        private final Provider<IPeerRegistry> peerRegistryProvider;
        private volatile IPeerRegistry peerRegistry;

        private PeerFilter peerFilter;
        private ConcurrentMap<String, Tracker> trackers;

        @Inject
        PeerTrackerService(Provider<IPeerRegistry> peerRegistryProvider, PeerFilter peerFilter) {
            this.peerRegistryProvider = peerRegistryProvider;
            this.peerFilter = peerFilter;
            this.trackers = new ConcurrentHashMap<>();
        }

        @Override
        public boolean isSupportedProtocol(String trackerUrl) {
            return true;
        }

        @Override
        public Tracker getTracker(String trackerUrl) {
            Tracker tracker = trackers.get(trackerUrl);
            if (tracker == null) {
                tracker = new Tracker() {
                    private TrackerRequestBuilder requestBuilder =
                            new TrackerRequestBuilder(TorrentId.fromBytes(new byte[TorrentId.length()])) {

                                @Override
                                public TrackerResponse start() {
                                    knownPeersService.addPeer(trackerUrl, getLocalPeer());
                                    return queryPeers();
                                }

                                @Override
                                public TrackerResponse stop() {
                                    knownPeersService.removePeer(trackerUrl, getLocalPeer());
                                    return TrackerResponse.ok();
                                }

                                @Override
                                public TrackerResponse complete() {
                                    return TrackerResponse.ok();
                                }

                                @Override
                                public TrackerResponse query() {
                                    return queryPeers();
                                }

                                private TrackerResponse queryPeers() {
                                    return new StartResponse(peerFilter.filterPeers(
                                            getLocalPeer(), knownPeersService.getPeersSnapshot(trackerUrl)));
                                }
                    };

                    @Override
                    public TrackerRequestBuilder request(TorrentId torrentId) {
                        return requestBuilder;
                    }
                };

                Tracker existing = trackers.putIfAbsent(trackerUrl, tracker);

                if (existing != null) {
                    tracker = existing;
                }
            }
            return tracker;
        }

        private Peer getLocalPeer() {
            if (peerRegistry == null) {
                synchronized (peerRegistryProvider) {
                    if (peerRegistry == null) {
                        peerRegistry = peerRegistryProvider.get();
                    }
                }
            }
            return peerRegistry.getLocalPeer();
        }

        @Override
        public Tracker getTracker(AnnounceKey announceKey) {
            if (announceKey.isMultiKey()) {
                throw new IllegalStateException("Multi keys not supported: " + announceKey);
            }
            return getTracker(announceKey.getTrackerUrl());
        }
    }

    private static KnownPeersService knownPeersService = new KnownPeersService();

    private static class KnownPeersService {

        private ConcurrentMap<String, Set<Peer>> knownPeers;
        private ReentrantReadWriteLock lock;

        KnownPeersService() {
            knownPeers = new ConcurrentHashMap<>();
            lock = new ReentrantReadWriteLock(true);
        }

        public Set<Peer> getPeersSnapshot(String trackerUrl) {
            lock.readLock().lock();
            try {
                Set<Peer> peers = knownPeers.get(trackerUrl);
                if (peers == null) {
                    return Collections.emptySet();
                }

                return new HashSet<>(peers);

            } finally {
                lock.readLock().unlock();
            }
        }

        public void addPeer(String trackerUrl, Peer peer) {
            lock.writeLock().lock();
            try {
                Set<Peer> peers = knownPeers.get(trackerUrl);
                if (peers == null) {
                    peers = new HashSet<>();
                    knownPeers.put(trackerUrl, peers);
                }
                peers.add(peer);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public void removePeer(String trackerUrl, Peer peer) {
            lock.writeLock().lock();
            try {
                Set<Peer> peers = knownPeers.get(trackerUrl);
                if (peers == null) {
                    return;
                }
                peers.remove(peer);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    private static class StartResponse extends TrackerResponse {

        private Collection<Peer> peers;

        StartResponse(Collection<Peer> peers) {
            this.peers = peers;
        }

        @Override
        public int getInterval() {
            return 0;
        }

        @Override
        public int getMinInterval() {
            return 0;
        }

        @Override
        public Iterable<Peer> getPeers() {
            return peers;
        }
    }
}
