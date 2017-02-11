package bt.torrent.messaging;

import bt.BtException;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.net.Peer;
import bt.protocol.Interested;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Request;
import bt.torrent.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.annotation.Produces;
import bt.torrent.data.BlockWrite;
import bt.torrent.selector.PieceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Produces block requests to the remote peer.
 *
 * @since 1.0
 */
public class RequestProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProducer.class);

    private static final int MAX_PENDING_REQUESTS = 3;

    private Bitfield bitfield;
    private Assignments assignments;
    private BitfieldBasedStatistics pieceStatistics;
    private PieceSelector selector;
    private List<ChunkDescriptor> chunks;

    RequestProducer(DataDescriptor dataDescriptor,
                    BitfieldBasedStatistics pieceStatistics,
                    PieceSelector selector,
                    Assignments assignments) {
        this.bitfield = dataDescriptor.getBitfield();
        this.chunks = dataDescriptor.getChunkDescriptors();
        this.pieceStatistics = pieceStatistics;
        this.selector = selector;
        this.assignments = assignments;
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {

        Peer peer = context.getPeer();
        ConnectionState connectionState = context.getConnectionState();

        if (bitfield.getPiecesRemaining() == 0) {
            if (connectionState.isInterested()) {
                messageConsumer.accept(NotInterested.instance());
            }
            return;
        }

        // periodically refresh available pieces;
        if (System.currentTimeMillis() - connectionState.getLastCheckedAvailablePiecesForPeer() >= 3000) {
            selectPieceAndUpdateConnection(connectionState, peer, messageConsumer);
        }

        if (connectionState.isPeerChoking()) {
            if (assignments.hasAssignedPiece(peer)) {
                assignments.removeAssignment(peer);
                connectionState.getRequestQueue().clear();
                connectionState.getPendingRequests().clear();
            }
            return;
        }

        if (assignments.hasAssignedPiece(peer)) {
            int currentPiece = assignments.getAssignedPiece(peer).get();
            if (bitfield.isComplete(currentPiece)) {
                assignments.removeAssignment(peer);
                connectionState.getRequestQueue().clear();
                connectionState.getPendingRequests().clear();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished downloading piece #{}", currentPiece);
                }
            } else if (connectionState.getRequestQueue().isEmpty()) {
                if (System.currentTimeMillis() - connectionState.getLastBuiltRequests() >= 30000) {
                    // this may happen when some of the received blocks were discarded by the data worker
                    // or some of the requests/responses were lost on the network;
                    // here we again create requests for the missing blocks;
                    // consider this to be a kind of tradeoff between memory consumption
                    // (internal capacity of the data worker) and additional network overhead from the duplicate requests
                    // while ensuring that the piece WILL be downloaded eventually
                    initializeRequestQueue(connectionState, currentPiece);
                }
            }
        }

        if (!assignments.hasAssignedPiece(peer)) {
            Optional<Integer> selectedPiece = selectPieceAndUpdateConnection(connectionState, peer, messageConsumer);
            if (selectedPiece.isPresent()) {
                assignments.assignPiece(peer, selectedPiece.get());
                initializeRequestQueue(connectionState, selectedPiece.get());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Begin downloading piece #{}", selectedPiece.get());
                }
            }

            Queue<Request> requestQueue = connectionState.getRequestQueue();
            while (!requestQueue.isEmpty() && connectionState.getPendingRequests().size() <= MAX_PENDING_REQUESTS) {
                Request request = requestQueue.poll();
                Object key = Mapper.mapper().buildKey(request.getPieceIndex(), request.getOffset(), request.getLength());
                messageConsumer.accept(request);
                connectionState.getPendingRequests().add(key);
            }
        }
    }

    private Optional<Integer> selectPieceAndUpdateConnection(ConnectionState connectionState,
                                                             Peer peer,
                                                             Consumer<Message> messageConsumer) {
        Optional<Integer> selectedPiece = selectPieceForPeer(peer);
        if (selectedPiece.isPresent()) {
            if (!connectionState.isInterested()) {
                messageConsumer.accept(Interested.instance());
            }
        } else {
            messageConsumer.accept(NotInterested.instance());
        }
        connectionState.setLastCheckedAvailablePiecesForPeer(System.currentTimeMillis());
        return selectedPiece;
    }

    private Optional<Integer> selectPieceForPeer(Peer peer) {
        Optional<Bitfield> peerBitfield = pieceStatistics.getPeerBitfield(peer);
        if (peerBitfield.isPresent()) {
            List<Integer> pieces = selector.getNextPieces(pieceStatistics)
                    .filter(piece -> peerBitfield.get().getPieceStatus(piece) == Bitfield.PieceStatus.COMPLETE_VERIFIED)
                    .limit(1)
                    .collect(Collectors.toList());
            if (pieces.size() > 0) {
                Integer pieceIndex = pieces.get(0);
                return Optional.of(pieceIndex);
            }
        }
        return Optional.empty();
    }

    private void initializeRequestQueue(ConnectionState connectionState, int pieceIndex) {
        connectionState.getRequestQueue().clear();
        connectionState.getRequestQueue().addAll(
                buildRequests(pieceIndex).stream()
                    .filter(request -> {
                        Object key = Mapper.mapper().buildKey(
                            request.getPieceIndex(), request.getOffset(), request.getLength());
                        if (connectionState.getPendingRequests().contains(key)) {
                            return false;
                        }

                        CompletableFuture<BlockWrite> future = connectionState.getPendingWrites().get(key);
                        if (future == null) {
                            return true;
                        } else if (!future.isDone()) {
                            return false;
                        }

                        boolean failed = future.isDone() && future.getNow(null).getError().isPresent();
                        if (failed) {
                            connectionState.getPendingWrites().remove(key);
                        }
                        return failed;

                    }).collect(Collectors.toList()));

        connectionState.setLastBuiltRequests(System.currentTimeMillis());
    }

    private Collection<Request> buildRequests(int pieceIndex) {
        List<Request> requests = new ArrayList<>();
        ChunkDescriptor chunk = chunks.get(pieceIndex);
        long chunkSize = chunk.getSize();
        long blockSize = chunk.getBlockSize();

        for (int blockIndex = 0; blockIndex < chunk.getBlockCount(); blockIndex++) {
            if (!chunk.isBlockVerified(blockIndex)) {
                int offset = (int) (blockIndex * blockSize);
                int length = (int) Math.min(blockSize, chunkSize - offset);
                try {
                    requests.add(new Request(pieceIndex, offset, length));
                } catch (InvalidMessageException e) {
                    // shouldn't happen
                    throw new BtException("Unexpected error", e);
                }
            }
        }
        return requests;
    }
}
