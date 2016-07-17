package bt.torrent;

import bt.BtException;
import bt.data.IChunkDescriptor;
import bt.net.Peer;
import bt.service.IShutdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DataWorker implements IDataWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataWorker.class);

    private List<IChunkDescriptor> chunks;

    private BlockingQueue<BlockOp> pendingOps;
    private Map<Peer, BlockingQueue<BlockRead>> completedBlockRequests;

    private volatile Thread t;
    private volatile boolean shutdown;

    public DataWorker(IShutdownService shutdownService, List<IChunkDescriptor> chunks, int maxQueueLength) {

        this.chunks = chunks;

        pendingOps = new LinkedBlockingQueue<>(maxQueueLength);
        completedBlockRequests = new HashMap<>();

        shutdownService.addShutdownHook(this::shutdown);
    }

    @Override
    public void run() {

        t = Thread.currentThread();
        t.setPriority(Thread.MIN_PRIORITY);

        while (!shutdown) {
            try {
                pendingOps.take().execute();
            } catch (InterruptedException e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Interrupted while waiting for next block op", e);
                }
            }
        }
    }

    private void shutdown() {
        shutdown = true;
        if (t != null) {
            t.interrupt();
        }
    }

    @Override
    public boolean addBlockRequest(Peer peer, int pieceIndex, int offset, int length) {

        if (pieceIndex < 0 || pieceIndex >= chunks.size()) {
            throw new BtException("invalid piece index: " + pieceIndex);
        }
        ReadOp readOp = new ReadOp(peer, pieceIndex, offset, length);
        boolean accepted = pendingOps.offer(readOp);
        if (!accepted) {
            LOGGER.warn("Can't accept read block request (" + readOp + ") -- queue is full");
        }
        return accepted;
    }

    @Override
    public BlockWrite addBlock(Peer peer, int pieceIndex, int offset, byte[] block) {

        if (pieceIndex < 0 || pieceIndex >= chunks.size()) {
            throw new BtException("invalid piece index: " + pieceIndex);
        }

        BlockWrite blockWrite = new BlockWrite(peer, pieceIndex, offset, block);
        WriteOp writeOp = new WriteOp(peer, blockWrite);
        boolean accepted = pendingOps.offer(writeOp);
        if (!accepted) {
            LOGGER.warn("Can't accept write block request (" + writeOp + ") -- queue is full");
            blockWrite.setSuccess(false);
            blockWrite.setComplete();
        }
        return blockWrite;
    }

    @Override
    public BlockRead getCompletedBlockRequest(Peer peer) {
        BlockingQueue<BlockRead> peerCompletedRequests = completedBlockRequests.get(peer);
        return (peerCompletedRequests == null) ? null : peerCompletedRequests.poll();
    }

    private interface BlockOp {
        void execute();
    }

    private class ReadOp implements BlockOp {

        private Peer peer;
        private int pieceIndex;
        private int offset;
        private int length;

        ReadOp(Peer peer, int pieceIndex, int offset, int length) {
            this.peer = Objects.requireNonNull(peer);
            this.pieceIndex = pieceIndex;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void execute() {
            try {
                IChunkDescriptor chunk = chunks.get(pieceIndex);
                byte[] block = chunk.readBlock(offset, length);

                BlockingQueue<BlockRead> peerCompletedRequests = completedBlockRequests.get(peer);
                if (peerCompletedRequests == null) {
                    peerCompletedRequests = new LinkedBlockingQueue<>();
                    completedBlockRequests.put(peer, peerCompletedRequests);
                }
                peerCompletedRequests.add(new BlockRead(peer, pieceIndex, offset, length, block));
            } catch (Exception e) {
                LOGGER.error("Failed to process read block request (" + toString() + ") for peer: " + peer, e);
            }
        }

        @Override
        public String toString() {
            return "piece index {" + pieceIndex + "}, offset {" + offset +
                "}, length {" + length + "}";
        }
    }

    private class WriteOp implements BlockOp {

        private Peer peer;
        private BlockWrite blockWrite;

        WriteOp(Peer peer, BlockWrite blockWrite) {
            this.peer = Objects.requireNonNull(peer);
            this.blockWrite = blockWrite;
        }

        @Override
        public void execute() {
            IChunkDescriptor chunk = chunks.get(blockWrite.getPieceIndex());
            try {
                chunk.writeBlock(blockWrite.getBlock(), blockWrite.getOffset());
                blockWrite.setSuccess(true);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Successfully processed block (" + toString() + ") from peer: " + peer);
                }
                // TODO: perform verification asynchronously in a separate dedicated thread
                if (chunk.verify() && LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Successfully verified block (" + toString() + ")");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process block (" + toString() + ") from peer: " + peer, e);
                blockWrite.setSuccess(false);
            }
            blockWrite.setComplete();
        }

        @Override
        public String toString() {
            return "piece index {" + blockWrite.getPieceIndex() + "}, offset {" + blockWrite.getOffset() +
                    "}, block {" + blockWrite.getBlock().length + " bytes}";
        }
    }
}
