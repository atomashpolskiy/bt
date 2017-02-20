package bt.torrent.messaging;

import bt.BtException;
import bt.data.ChunkDescriptor;
import bt.data.DataDescriptor;
import bt.net.Peer;
import bt.protocol.Cancel;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Request;
import bt.torrent.Bitfield;
import bt.torrent.annotation.Produces;
import bt.torrent.data.BlockWrite;
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

    private static final int MAX_PENDING_REQUESTS = 5;

    private Bitfield bitfield;
    private Assignments assignments;
    private List<ChunkDescriptor> chunks;

    RequestProducer(DataDescriptor dataDescriptor,
                    Assignments assignments) {
        this.bitfield = dataDescriptor.getBitfield();
        this.chunks = dataDescriptor.getChunkDescriptors();
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

        Optional<Integer> currentAssignment = connectionState.getCurrentAssignment();
        if (!currentAssignment.isPresent()) {
            currentAssignment = assignments.pollNextAssignment(peer);
            if (currentAssignment.isPresent()) {
                connectionState.setCurrentAssignment(currentAssignment.get());
            }
        }
        if (currentAssignment.isPresent()) {
            int currentPiece = currentAssignment.get();
            if (bitfield.isComplete(currentPiece)) {
                connectionState.getPendingRequests().forEach(r -> {
                    Mapper.decodeKey(r).ifPresent(key -> {
                        messageConsumer.accept(new Cancel(key.getPieceIndex(), key.getOffset(), key.getLength()));
                    });
                });
                connectionState.getPendingWrites().clear();
                resetConnection(connectionState, Optional.empty());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished downloading piece #{}", currentPiece);
                }
            } else if (!connectionState.initializedRequestQueue()) {
                initializeRequestQueue(connectionState, currentPiece);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Begin downloading piece #{} from peer: {}", currentPiece, peer);
                }
            } else if (connectionState.getLastReceivedBlock().isPresent()
                    && (System.currentTimeMillis() - connectionState.getLastReceivedBlock().get() >= 10000)) {
                // this may happen when some of the received blocks were discarded by the data worker
                // or some of the requests/responses were lost on the network;
                // here we again create requests for the missing blocks;
                // consider this to be a kind of tradeoff between memory consumption
                // (internal capacity of the data worker) and additional network overhead from the duplicate requests
                // while ensuring that the piece WILL be downloaded eventually
                resetConnection(connectionState, currentAssignment);
                initializeRequestQueue(connectionState, currentPiece);
            }
        }

        if (connectionState.initializedRequestQueue()) {
            Queue<Request> requestQueue = connectionState.getRequestQueue();
            while (!requestQueue.isEmpty() && connectionState.getPendingRequests().size() <= MAX_PENDING_REQUESTS) {
                Request request = requestQueue.poll();
                Object key = Mapper.mapper().buildKey(request.getPieceIndex(), request.getOffset(), request.getLength());
                messageConsumer.accept(request);
                connectionState.getPendingRequests().add(key);
            }
        }
    }

    private void resetConnection(ConnectionState connectionState, Optional<Integer> currentPiece) {
        connectionState.onUnassign();
        if (currentPiece.isPresent()) {
            connectionState.setCurrentAssignment(currentPiece.get());
        }
    }

    private void initializeRequestQueue(ConnectionState connectionState, int pieceIndex) {
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

        connectionState.setInitializedRequestQueue(true);
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
