package bt.torrent.data;

import bt.data.IChunkDescriptor;
import bt.data.IDataDescriptor;
import bt.net.Peer;
import bt.service.IRuntimeLifecycleBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DataWorker implements IDataWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataWorker.class);

    private List<IChunkDescriptor> chunks;

    private final ExecutorService executor;
    private final int maxPendingTasks;
    private final AtomicInteger pendingTasksCount;

    private Set<Consumer<Integer>> verifiedPieceListeners;

    private volatile Thread t;
    private volatile boolean shutdown;

    public DataWorker(IRuntimeLifecycleBinder lifecycleBinder, IDataDescriptor dataDescriptor, int maxQueueLength) {
        this.chunks = dataDescriptor.getChunkDescriptors();
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            private AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "dataworker-pool-thread-" + i.incrementAndGet());
            }
        });
        this.maxPendingTasks = maxQueueLength;
        this.pendingTasksCount = new AtomicInteger();

        verifiedPieceListeners = ConcurrentHashMap.newKeySet();

        lifecycleBinder.onShutdown(this.getClass().getName() + " - " + dataDescriptor, this.executor::shutdownNow);
    }

    @Override
    public void addVerifiedPieceListener(Consumer<Integer> listener) {
        verifiedPieceListeners.add(listener);
    }

    @Override
    public CompletableFuture<BlockRead> addBlockRequest(Peer peer, int pieceIndex, int offset, int length) {
        if (pendingTasksCount.get() >= maxPendingTasks) {
            LOGGER.warn("Can't accept read block request from peer (" + peer + ") -- queue is full");
            return CompletableFuture.completedFuture(BlockRead.rejected(peer, pieceIndex, offset));
        } else {
            pendingTasksCount.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    IChunkDescriptor chunk = chunks.get(pieceIndex);
                    byte[] block = chunk.readBlock(offset, length);
                    return BlockRead.complete(peer, pieceIndex, offset, block);
                } catch (Throwable e) {
                    return BlockRead.exceptional(peer, e, pieceIndex, offset);
                } finally {
                    pendingTasksCount.decrementAndGet();
                }
            }, executor);
        }
    }

    @Override
    public CompletableFuture<BlockWrite> addBlock(Peer peer, int pieceIndex, int offset, byte[] block) {
        if (pendingTasksCount.get() >= maxPendingTasks) {
            LOGGER.warn("Can't accept write block request -- queue is full");
            return CompletableFuture.completedFuture(BlockWrite.rejected(peer, pieceIndex, offset, block));
        } else {
            pendingTasksCount.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    IChunkDescriptor chunk = chunks.get(pieceIndex);
                    chunk.writeBlock(block, offset);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Successfully processed block (" + toString() + ") from peer: " + peer);
                    }
                    // TODO: perform verification asynchronously in a separate dedicated thread
                    if (chunk.verify()) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Successfully verified block (" + toString() + ")");
                        }
                        verifiedPieceListeners.forEach(listener -> listener.accept(pieceIndex));
                    }
                    return BlockWrite.complete(peer, pieceIndex, offset, block);
                } catch (Throwable e) {
                    return BlockWrite.exceptional(peer, e, pieceIndex, offset, block);
                } finally {
                    pendingTasksCount.decrementAndGet();
                }
            }, executor);
        }
    }
}
