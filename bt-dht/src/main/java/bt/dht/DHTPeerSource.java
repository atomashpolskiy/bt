package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.peer.PeerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class DHTPeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DHTPeerSource.class);

    private final Torrent torrent;
    private final DHTService dhtService;
    private final ExecutorService executor;

    private final ReentrantLock lock;
    private final AtomicReference<Future<Collection<Peer>>> futureOptional;
    private volatile Collection<Peer> peers;

    DHTPeerSource(Torrent torrent, DHTService dhtService, ExecutorService executor) {
        this.torrent = torrent;
        this.dhtService = dhtService;
        this.executor = executor;

        this.lock = new ReentrantLock();
        this.futureOptional = new AtomicReference<>();
        this.peers = Collections.emptyList();
    }

    @Override
    public boolean update() {
        lock.lock();
        try {
            if (futureOptional.get() != null) {
                Future<Collection<Peer>> future = futureOptional.get();
                if (future.isDone()) {
                    try {
                        peers = future.get();
                        futureOptional.set(null);
                        return true;
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.error("Failed to execute future", e);
                        peers = Collections.emptyList();
                    }
                }
            } else {
                futureOptional.set(executor.submit(() -> dhtService.getPeers(torrent)));
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
    }
}
