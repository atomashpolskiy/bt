package peertracker;

import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.net.PeerActivityListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PeerStats implements PeerActivityListener {

    public static class Counter {
        private final AtomicLong discoveredTimes = new AtomicLong();
        private final AtomicLong connectedTimes = new AtomicLong();
        private final AtomicLong disconnectedTimes = new AtomicLong();

        public void incrementDiscovered() {
            discoveredTimes.addAndGet(1);
        }

        public void incrementConnected() {
            connectedTimes.addAndGet(1);
        }

        public void incrementDisconnected() {
            disconnectedTimes.addAndGet(1);
        }

        public long getDiscoveredTimes() {
            return discoveredTimes.get();
        }

        public long getConnectedTimes() {
            return connectedTimes.get();
        }

        public long getDisconnectedTimes() {
            return disconnectedTimes.get();
        }
    }

    private final Map<Peer, Counter> counters = new ConcurrentHashMap<>(5000);

    @Override
    public void onPeerDiscovered(Peer peer) {
        getCounter(peer).incrementDiscovered();
    }

    @Override
    public void onPeerConnected(TorrentId torrentId, Peer peer) {
        getCounter(peer).incrementConnected();
    }

    @Override
    public void onPeerDisconnected(TorrentId torrentId, Peer peer) {
        getCounter(peer).incrementDisconnected();
    }

    private Counter getCounter(Peer peer) {
        Counter counter = counters.get(peer);
        if (counter == null) {
            counter = new Counter();
            Counter existing = counters.putIfAbsent(peer, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        return counter;
    }

    public Map<Peer, Counter> getCounters() {
        return counters;
    }
}
