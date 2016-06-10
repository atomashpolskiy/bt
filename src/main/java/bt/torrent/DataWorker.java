package bt.torrent;

import bt.BtException;
import bt.data.IChunkDescriptor;
import bt.net.Peer;
import bt.protocol.Piece;
import bt.protocol.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DataWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataWorker.class);

    private List<IChunkDescriptor> chunks;

    private BlockingQueue<BlockWrite> blocks;
    private BlockingQueue<BlockRead> blockRequests;
    private Map<Peer, BlockingQueue<BlockRead>> completedBlockRequests;

    public DataWorker(List<IChunkDescriptor> chunks, int maxQueueLength) {

        this.chunks = chunks;

        blocks = new LinkedBlockingQueue<>();
        blockRequests = new ArrayBlockingQueue<>(maxQueueLength);
        completedBlockRequests = new HashMap<>();
    }

    @Override
    public void run() {
        while (true) {
            processReadRequest(blockRequests.poll());
            processWriteRequest(blocks.poll());
        }
    }

    private void processReadRequest(BlockRead readRequest) {

        if (readRequest == null) {
            return;
        }

        Request request = readRequest.getRequest();
        try {
            IChunkDescriptor chunk = chunks.get(request.getPieceIndex());
            byte[] block = chunk.readBlock(request.getOffset(), request.getLength());
            readRequest.setBlock(block);

            BlockingQueue<BlockRead> peerCompletedRequests = completedBlockRequests.get(readRequest.getPeer());
            if (peerCompletedRequests == null) {
                peerCompletedRequests = new LinkedBlockingQueue<>();
                completedBlockRequests.put(readRequest.getPeer(), peerCompletedRequests);
            }
            peerCompletedRequests.add(readRequest);
        } catch (Exception e) {
            LOGGER.error("Failed to process block request (" + request + ") for peer: " + readRequest.getPeer());
        }
    }

    private void processWriteRequest(BlockWrite writeRequest) {

        if (writeRequest == null) {
            return;
        }

        Piece piece = writeRequest.getPiece();
        int pieceIndex = piece.getPieceIndex();
        IChunkDescriptor chunk = chunks.get(pieceIndex);
        try {
            chunk.writeBlock(piece.getBlock(), piece.getOffset());
            writeRequest.setSuccess(true);
        } catch (Exception e) {
            LOGGER.error("Failed to process block (" + piece + ")");
            writeRequest.setSuccess(false);
        }
    }

    boolean addBlockRequest(Peer peer, Request request) {

        int pieceIndex = request.getPieceIndex();
        if (pieceIndex >= chunks.size()) {
            throw new BtException("piece index is too large: " + pieceIndex);
        }
        return blockRequests.offer(new BlockRead(peer, request));
    }

    BlockWrite addBlock(Piece piece) {

        int pieceIndex = piece.getPieceIndex();
        if (pieceIndex >= chunks.size()) {
            throw new BtException("piece index is too large: " + pieceIndex);
        }
        BlockWrite request = new BlockWrite(piece);
        blocks.add(request);
        return request;
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
}
