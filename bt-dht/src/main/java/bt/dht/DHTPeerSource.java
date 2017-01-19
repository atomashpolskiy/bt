package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.peer.PeerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class DHTPeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DHTPeerSource.class);

    private static final int MAX_PEERS_PER_BATCH = 10;

    private final Torrent torrent;
    private final DHTService dhtService;
    private final ExecutorService executor;

    private final ReentrantLock lock;
    private final AtomicReference<Future<?>> futureOptional;
    private final Queue<Peer> peers;

    DHTPeerSource(Torrent torrent, DHTService dhtService, ExecutorService executor) {
        this.torrent = torrent;
        this.dhtService = dhtService;
        this.executor = executor;

        this.lock = new ReentrantLock();
        this.futureOptional = new AtomicReference<>();
        this.peers = new LinkedBlockingQueue<>();
    }

    @Override
    public boolean update() {
        if (peers.isEmpty()) {
            schedulePeerCollection();
        }
        return !peers.isEmpty();
    }

    private void schedulePeerCollection() {
        if (lock.tryLock()) {
            try {
                if (futureOptional.get() != null) {
                    Future<?> future = futureOptional.get();
                    if (future.isDone()) {
                        try {
                            future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            LOGGER.warn("Peer collection finished with exception for torrent ID: " + torrent.getTorrentId(), e);
                        }
                        futureOptional.set(null);
                    }
                }

                if (futureOptional.get() == null) {
                    futureOptional.set(executor.submit(this::collectPeers));
                    LOGGER.info("Scheduled peer collection for torrent ID: " + torrent.getTorrentId());
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void collectPeers() {
        Stream<Peer> peerStream = dhtService.getPeers(torrent);
        peerStream.forEach(peer -> {
            peers.add(peer);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Collected new peer (torrent ID: %s, peer: %s)", torrent.getTorrentId(), peer));
            }
        });
        LOGGER.info("Peer collection finished for torrent ID: " + torrent.getTorrentId());
    }

    @Override
    public Collection<Peer> getPeers() {
        Set<Peer> batch = new HashSet<>((int)(MAX_PEERS_PER_BATCH / 0.75d + 1));
        int i = 0;
        Peer peer;
        while (i++ < MAX_PEERS_PER_BATCH && (peer = peers.poll()) != null) {
            batch.add(peer);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Collected a batch of " + batch.size() + " peers: " + batch);
        }
        return batch;
    }
}
