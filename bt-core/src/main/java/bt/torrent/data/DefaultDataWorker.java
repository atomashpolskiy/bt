package bt.torrent.data;

import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.net.Peer;
import bt.service.IRuntimeLifecycleBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class DefaultDataWorker implements DataWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataWorker.class);

    private List<ChunkDescriptor> chunks;

    private final ExecutorService executor;
    private final int maxPendingTasks;
    private final AtomicInteger pendingTasksCount;

    public DefaultDataWorker(IRuntimeLifecycleBinder lifecycleBinder, DataDescriptor dataDescriptor, int maxQueueLength) {
        this.chunks = dataDescriptor.getChunkDescriptors();
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            private AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "bt.torrent.data.worker-" + i.incrementAndGet());
            }
        });
        this.maxPendingTasks = maxQueueLength;
        this.pendingTasksCount = new AtomicInteger();

        lifecycleBinder.onShutdown("Shutdown data worker for descriptor: " + dataDescriptor, this.executor::shutdownNow);
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
                    ChunkDescriptor chunk = chunks.get(pieceIndex);
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
                    ChunkDescriptor chunk = chunks.get(pieceIndex);
                    chunk.writeBlock(block, offset);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Successfully processed block: " +
                                "piece index {" + pieceIndex + "}, offset {" + offset + "}, length {" + block.length + "}");
                    }

                    CompletableFuture<Boolean> verificationFuture = CompletableFuture.supplyAsync(chunk::verify, executor);

                    return BlockWrite.complete(peer, pieceIndex, offset, block, verificationFuture);
                } catch (Throwable e) {
                    return BlockWrite.exceptional(peer, e, pieceIndex, offset, block);
                } finally {
                    pendingTasksCount.decrementAndGet();
                }
            }, executor);
        }
    }
}
