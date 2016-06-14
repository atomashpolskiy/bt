package bt.torrent;

import bt.BtException;
import bt.data.IChunkDescriptor;
import bt.net.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DataWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataWorker.class);

    private List<IChunkDescriptor> chunks;

    private BlockingQueue<BlockOp> pendingOps;
    private Map<Peer, BlockingQueue<BlockRead>> completedBlockRequests;

    public DataWorker(List<IChunkDescriptor> chunks, int maxQueueLength) {

        this.chunks = chunks;

        pendingOps = new LinkedBlockingQueue<>(maxQueueLength);
        completedBlockRequests = new HashMap<>();
    }

    @Override
    public void run() {
        while (true) {
            try {
                pendingOps.take().execute();
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while waiting for next block op", e);
            }
        }
    }

    boolean addBlockRequest(Peer peer, int pieceIndex, int offset, int length) {

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

    BlockWrite addBlock(Peer peer, int pieceIndex, int offset, byte[] block) {

        if (pieceIndex < 0 || pieceIndex >= chunks.size()) {
            throw new BtException("invalid piece index: " + pieceIndex);
        }

        BlockWrite blockWrite = new BlockWrite(peer, pieceIndex, offset, block);
        WriteOp writeOp = new WriteOp(peer, blockWrite);
        boolean accepted = pendingOps.offer(writeOp);
        if (!accepted) {
            LOGGER.warn("Can't accept write block request (" + writeOp + ") -- queue is full");
        }
        return accepted? blockWrite : null;
    }

    Collection<BlockRead> getCompletedBlockRequests(Peer peer, int maxCount) {

        BlockingQueue<BlockRead> peerCompletedRequests = completedBlockRequests.get(peer);
        if (peerCompletedRequests != null) {

            Collection<BlockRead> completedRequests = new ArrayList<>(maxCount + 1);
            BlockRead peerRequest;
            int i = 0;
            while ((peerRequest = peerCompletedRequests.poll()) != null && ++i <= maxCount) {
                completedRequests.add(peerRequest);
            }
            return completedRequests;
        } else {
            return Collections.emptyList();
        }
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
            } catch (Exception e) {
                LOGGER.error("Failed to process block (" + toString() + ") for peer: " + peer, e);
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
