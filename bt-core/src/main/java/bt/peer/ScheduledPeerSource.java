package bt.peer;

import bt.net.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @since 1.1
 */
public abstract class ScheduledPeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledPeerSource.class);

    private final ExecutorService executor;
    private final ReentrantLock lock;
    private final AtomicReference<Future<?>> futureOptional;
    private final Queue<Peer> peers;

    public ScheduledPeerSource(ExecutorService executor) {
        this.executor = executor;
        this.lock = new ReentrantLock();
        this.futureOptional = new AtomicReference<>();
        this.peers = new LinkedBlockingQueue<>();
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
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
                            LOGGER.warn("Peer collection finished with exception in peer source: " + toString(), e);
                        }
                        futureOptional.set(null);
                    }
                }

                if (futureOptional.get() == null) {
                    futureOptional.set(executor.submit(() -> collectPeers(peers::add)));
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * @since 1.1
     */
    protected abstract void collectPeers(Consumer<Peer> peerConsumer);
}
