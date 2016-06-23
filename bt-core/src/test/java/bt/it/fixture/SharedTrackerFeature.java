package bt.it.fixture;

import bt.Constants;
import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import com.google.inject.Binder;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SharedTrackerFeature implements BtTestRuntimeFeature {

    @Override
    public void contributeToRuntime(BtTestRuntimeBuilder runtimeBuilder, Binder binder) {
        Peer peer = new InetPeer(runtimeBuilder.getAddress(), runtimeBuilder.getPort());
        binder.bind(ITrackerService.class).toInstance(new PeerTrackerService(peer));
    }

    private static class PeerTrackerService implements ITrackerService {

        private Peer peer;
        private ConcurrentMap<URL, Tracker> trackers;

        PeerTrackerService(Peer peer) {
            this.peer = peer;
            trackers = new ConcurrentHashMap<>();
        }

        @Override
        public Tracker getTracker(URL baseUrl) {

            Tracker tracker = trackers.get(baseUrl);
            if (tracker == null) {

                tracker = new Tracker() {

                    private TrackerRequestBuilder requestBuilder =
                            new TrackerRequestBuilder(new byte[Constants.INFO_HASH_LENGTH]) {

                        @Override
                        public TrackerResponse start() {

                            knownPeersService.addPeer(baseUrl, peer);

                            Set<Peer> peers = knownPeersService.getPeersSnapshot(baseUrl);
                            peers.remove(peer);
                            return new StartResponse(peers);
                        }

                        @Override
                        public TrackerResponse stop() {
                            knownPeersService.removePeer(baseUrl, peer);
                            return null;
                        }

                        @Override
                        public TrackerResponse complete() {
                            return null;
                        }

                        @Override
                        public TrackerResponse query() {
                            Set<Peer> peers = knownPeersService.getPeersSnapshot(baseUrl);
                            peers.remove(peer);
                            return new StartResponse(peers);
                        }
                    };

                    @Override
                    public TrackerRequestBuilder request(Torrent torrent) {
                        return requestBuilder;
                    }
                };

                Tracker existing = trackers.putIfAbsent(baseUrl, tracker);

                if (existing != null) {
                    tracker = existing;
                }
            }
            return tracker;
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

        private Set<Peer> getPeersSnapshot(URL trackerUrl) {
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
            super(Boolean.TRUE);
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
