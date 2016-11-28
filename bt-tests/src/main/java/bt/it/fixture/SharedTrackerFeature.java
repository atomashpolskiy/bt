package bt.it.fixture;

import bt.runtime.BtRuntimeBuilder;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.service.IPeerRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SharedTrackerFeature implements BtTestRuntimeFeature {

    private PeerFilter peerFilter;

    public SharedTrackerFeature() {
        this(new DefaultPeerFilter());
    }

    public SharedTrackerFeature(PeerFilter peerFilter) {
        this.peerFilter = peerFilter;
    }

    @Override
    public void contributeToRuntime(BtTestRuntimeConfiguration configuration, BtRuntimeBuilder runtimeBuilder) {

        runtimeBuilder.module(binder -> {
            binder.bind(PeerFilter.class).toInstance(peerFilter);
        });

        runtimeBuilder.module(binder ->
                binder.bind(ITrackerService.class).to(PeerTrackerService.class).in(Singleton.class));
    }

    public interface PeerFilter {
        Collection<Peer> filterPeers(Peer self, Set<Peer> peers);
    }

    private static class DefaultPeerFilter implements PeerFilter {

        @Override
        public Collection<Peer> filterPeers(Peer self, Set<Peer> peers) {
            peers.remove(self);
            return peers;
        }
    }

    private static class PeerTrackerService implements ITrackerService {

        private Peer peer;
        private PeerFilter peerFilter;
        private ConcurrentMap<URL, Tracker> trackers;

        @Inject
        PeerTrackerService(IPeerRegistry peerRegistry, PeerFilter peerFilter) {
            peer = peerRegistry.getLocalPeer();
            this.peerFilter = peerFilter;
            trackers = new ConcurrentHashMap<>();
        }

        @Override
        public Tracker getTracker(String url) {

            URL trackerUrl;
            try {
                trackerUrl = new URL(url);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Unexpected error", e);
            }
            Tracker tracker = trackers.get(trackerUrl);
            if (tracker == null) {

                tracker = new Tracker() {

                    private TrackerRequestBuilder requestBuilder =
                            new TrackerRequestBuilder(TorrentId.fromBytes(new byte[TorrentId.length()])) {

                                @Override
                                public TrackerResponse start() {
                                    knownPeersService.addPeer(trackerUrl, peer);
                                    return queryPeers();
                                }

                                @Override
                                public TrackerResponse stop() {
                                    knownPeersService.removePeer(trackerUrl, peer);
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
                                            peer, knownPeersService.getPeersSnapshot(trackerUrl)));
                                }
                    };

                    @Override
                    public TrackerRequestBuilder request(Torrent torrent) {
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

        private ConcurrentMap<URL, Set<Peer>> knownPeers;
        private ReentrantReadWriteLock lock;

        KnownPeersService() {
            knownPeers = new ConcurrentHashMap<>();
            lock = new ReentrantReadWriteLock(true);
        }

        public Set<Peer> getPeersSnapshot(URL trackerUrl) {
            lock.readLock().lock();
            try {
                Set<Peer> peers = knownPeers.get(trackerUrl);
                if (peers == null) {
                    return Collections.emptySet();
                }

                Set<Peer> snapshot = new HashSet<>((int)(peers.size() / 0.75) + 1);
                Collections.addAll(snapshot, peers.toArray(new Peer[peers.size()]));
                return snapshot;

            } finally {
                lock.readLock().unlock();
            }
        }

        public void addPeer(URL trackerUrl, Peer peer) {
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

        public void removePeer(URL trackerUrl, Peer peer) {
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
