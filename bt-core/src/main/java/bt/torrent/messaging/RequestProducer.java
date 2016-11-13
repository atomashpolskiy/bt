package bt.torrent.messaging;

import bt.BtException;
import bt.data.IChunkDescriptor;
import bt.net.Peer;
import bt.protocol.Interested;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Request;
import bt.torrent.annotation.Produces;
import bt.torrent.data.BlockWrite;
import bt.torrent.IPieceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RequestProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProducer.class);

    private static final int MAX_PENDING_REQUESTS = 3;

    private IPieceManager pieceManager;
    private List<IChunkDescriptor> chunks;

    public RequestProducer(List<IChunkDescriptor> chunks, IPieceManager pieceManager) {
        this.chunks = chunks;
        this.pieceManager = pieceManager;
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {

        Peer peer = context.getPeer();
        ConnectionState connectionState = context.getConnectionState();

        Optional<Integer> currentPiece = pieceManager.getAssignedPiece(peer);
        if (connectionState.getRequestQueue().isEmpty()) {
            if (currentPiece.isPresent()) {
                if (pieceManager.checkPieceCompleted(currentPiece.get())) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Finished downloading piece #" + currentPiece.get() +
                                "; peer: " + peer);
                    }
                    connectionState.getPendingWrites().clear();
                }
                // TODO: what if peer just doesn't respond? or if some block writes have failed?
                // being overly optimistical here, need to add some fallback strategy to restart piece
                // (prob. with another peer, i.e. in another conn worker)
                if (connectionState.isPeerChoking()) {
                    pieceManager.unselectPieceForPeer(peer, currentPiece.get());
                    connectionState.getRequestQueue().clear();
                    connectionState.getPendingRequests().clear();
                }
            } else {
                if (!connectionState.getMightSelectPieceForPeer().isPresent() ||
                        // periodically refresh available pieces;
                        // as a bonus this also relieves us from clearing
                        // this flag manually after the current piece (if there is any) has been downloaded
                        (System.currentTimeMillis() - connectionState.getLastCheckedAvailablePiecesForPeer()) >= 3000) {
                    connectionState.setMightSelectPieceForPeer(Optional.of(pieceManager.mightSelectPieceForPeer(peer)));
                    connectionState.setLastCheckedAvailablePiecesForPeer(System.currentTimeMillis());
                }
                if (connectionState.getMightSelectPieceForPeer().isPresent() && connectionState.getMightSelectPieceForPeer().get()
                        && pieceManager.getBitfield().getPiecesRemaining() > 0) {
                    if (!connectionState.isInterested()) {
                        messageConsumer.accept(Interested.instance());
                    }
                } else if (connectionState.isInterested()) {
                    messageConsumer.accept(NotInterested.instance());
                }
            }
        }

        if (!connectionState.isPeerChoking()) {
            if (!currentPiece.isPresent()) {
                // some time might have passed since the last check, need to refresh
                connectionState.setMightSelectPieceForPeer(Optional.of(pieceManager.mightSelectPieceForPeer(peer)));
                connectionState.setLastCheckedAvailablePiecesForPeer(System.currentTimeMillis());
                if (connectionState.getMightSelectPieceForPeer().get()) {
                    Optional<Integer> nextPiece = pieceManager.selectPieceForPeer(peer);
                    if (nextPiece.isPresent()) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Begin downloading piece #" + nextPiece.get() +
                                    "; peer: " + peer);
                        }
                        connectionState.getRequestQueue().addAll(buildRequests(nextPiece.get()));
                        connectionState.setLastBuiltRequests(System.currentTimeMillis());
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Initializing request queue {piece #" + nextPiece.get() +
                                    ", total requests: " + connectionState.getRequestQueue().size() + "}");
                        }
                    }
                } else if (connectionState.isInterested()) {
                    messageConsumer.accept(NotInterested.instance());
                }
            } else if (connectionState.getRequestQueue().isEmpty() && (System.currentTimeMillis() - connectionState.getLastBuiltRequests()) >= 30000) {
                // this may happen when some of the received blocks were discarded by the data worker;
                // here we again create requests for the missing blocks;
                // consider this to be a kind of tradeoff between memory consumption
                // (internal capacity of the data worker) and additional network overhead from the duplicate requests
                // while ensuring that the piece WILL be downloaded eventually
                // TODO: in future this should be handled more intelligently by dynamic load balancing
                connectionState.getRequestQueue().addAll(
                        buildRequests(currentPiece.get()).stream()
                            .filter(request -> {
                                Object key = Mapper.mapper().buildKey(
                                    request.getPieceIndex(), request.getOffset(), request.getLength());
                                if (connectionState.getPendingRequests().contains(key)) {
                                    return false;
                                }

                                BlockWrite blockWrite = connectionState.getPendingWrites().get(key);
                                if (blockWrite == null) {
                                    return true;
                                }

                                boolean failed = blockWrite.isComplete() && !blockWrite.isSuccess();
                                if (failed) {
                                    connectionState.getPendingWrites().remove(key);
                                }
                                return failed;

                            }).collect(Collectors.toList()));

                connectionState.setLastBuiltRequests(System.currentTimeMillis());

                if (LOGGER.isTraceEnabled() && !connectionState.getRequestQueue().isEmpty()) {
                    LOGGER.trace("Re-initializing request queue {piece #" + currentPiece.get() +
                            ", total requests: " + connectionState.getRequestQueue().size() + "}");
                }
            }

            while (!connectionState.getRequestQueue().isEmpty() && connectionState.getPendingRequests().size() <= MAX_PENDING_REQUESTS) {
                Request request = connectionState.getRequestQueue().poll();
                Object key = Mapper.mapper().buildKey(
                            request.getPieceIndex(), request.getOffset(), request.getLength());
                if (!connectionState.getPendingRequests().contains(key)) {
                    messageConsumer.accept(request);
                    connectionState.getPendingRequests().add(key);
                }
            }
        }
    }

    private Collection<Request> buildRequests(int pieceIndex) {

        List<Request> requests = new ArrayList<>();

        IChunkDescriptor chunk = chunks.get(pieceIndex);
        byte[] bitfield = chunk.getBitfield();
        long blockSize = chunk.getBlockSize(),
             chunkSize = chunk.getSize();

        for (int i = 0; i < bitfield.length; i++) {
            if (bitfield[i] == 0) {
                int offset = (int) (i * blockSize);
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
