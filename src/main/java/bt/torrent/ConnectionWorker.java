package bt.torrent;

import bt.BtException;
import bt.net.IPeerConnection;
import bt.protocol.Bitfield;
import bt.protocol.Cancel;
import bt.protocol.Choke;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Piece;
import bt.protocol.Request;
import bt.protocol.Unchoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConnectionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionWorker.class);

    private static final int MAX_PENDING_REQUESTS = 3;

    private IPieceManager pieceManager;

    private Consumer<Request> requestConsumer;
    private Function<Piece, BlockWrite> blockConsumer;
    private Supplier<BlockRead> blockSupplier;

    private final IPeerConnection connection;
    private ConnectionState connectionState;

    private long lastBuiltRequests;

    private long received;
    private long sent;

    private Optional<Integer> currentPiece;

    private Queue<Request> requestQueue;
    private Set<Object> pendingRequests;
    private Map<Object, BlockWrite> pendingWrites;
    private Set<Object> cancelledPeerRequests;

    private volatile boolean initialized;

    ConnectionWorker(IPeerConnection connection, IPieceManager pieceManager, Consumer<Request> requestConsumer,
                     Function<Piece, BlockWrite> blockConsumer, Supplier<BlockRead> blockSupplier) {

        this.pieceManager = pieceManager;

        this.requestConsumer = requestConsumer;
        this.blockConsumer = blockConsumer;
        this.blockSupplier = blockSupplier;

        this.connection = connection;
        connectionState = new ConnectionState();

        currentPiece = Optional.empty();

        requestQueue = new LinkedBlockingQueue<>();
        pendingRequests = new HashSet<>();
        pendingWrites = new HashMap<>();
        cancelledPeerRequests = new HashSet<>();
    }

    public long getReceived() {
        return received;
    }

    public long getSent() {
        return sent;
    }

    public IConnectionState getState() {
        return connectionState;
    }

    public void doWork() {

        // TODO: this is temporary fix, need to incorporate bitfield sending into the connection initialization chain
        if (!initialized && pieceManager.haveAnyData()) {
            Bitfield bitfield = new Bitfield(pieceManager.getBitfield());
            connection.postMessage(bitfield);
            initialized = true;
        }

        checkConnection();
        processIncomingMessages();
        processOutgoingMessages();
        connectionState.updateConnection(connection);
    }

    private void checkConnection() {
        if (connection.isClosed()) {
            throw new BtException("Connection is closed: " + connection.getRemotePeer());
        }
    }

    private void processIncomingMessages() {

        Message message = connection.readMessageNow();
        if (message != null) {

            switch (message.getType()) {
                case KEEPALIVE: {
                    break;
                }
                case BITFIELD: {
                    Bitfield bitfield = (Bitfield) message;
                    pieceManager.peerHasBitfield(connection, bitfield.getBitfield());
                    break;
                }
                case CHOKE: {
                    connectionState.setPeerChoking(true);
                    break;
                }
                case UNCHOKE: {
                    connectionState.setPeerChoking(false);
                    break;
                }
                case INTERESTED: {
                    connectionState.setPeerInterested(true);
                    break;
                }
                case NOT_INTERESTED: {
                    connectionState.setPeerInterested(false);
                    break;
                }
                case HAVE: {
                    Have have = (Have) message;
                    pieceManager.peerHasPiece(connection, have.getPieceIndex());
                    break;
                }
                case REQUEST: {
                    if (!connectionState.isChoking()) {
                        Request request = (Request) message;
                        requestConsumer.accept(request);
                    }
                    break;
                }
                case CANCEL: {
                    Cancel cancel = (Cancel) message;
                    cancelledPeerRequests.add(Mapper.mapper().buildKey(
                            cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength()));
                    break;
                }
                case PIECE: {
                    Piece piece = (Piece) message;

                    int pieceIndex = piece.getPieceIndex(),
                        offset = piece.getOffset();
                    byte[] block = piece.getBlock();
                    // check that this block was requested in the first place
                    Object key = Mapper.mapper().buildKey(pieceIndex, offset, block.length);
                    if (!pendingRequests.remove(key)) {
                        throw new BtException("Received unexpected block " + piece +
                                " from peer: " + connection.getRemotePeer());
                    } else {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace(requestQueue.size() + " requests left in queue {piece #" + currentPiece.get() + "}");
                        }
                        BlockWrite blockWrite = blockConsumer.apply(piece);
                        pendingWrites.put(key, blockWrite);

                    }
                    break;
                }
                case PORT: {
                    // ignore
                    break;
                }
                default: {
                    throw new BtException("Unexpected message type: " + message);
                }
            }
        }
    }

    private void processOutgoingMessages() {

        BlockRead block;
        while ((block = blockSupplier.get()) != null) {
            int pieceIndex = block.getPieceIndex(),
                offset = block.getOffset(),
                length = block.getLength();

            // check that peer hadn't sent cancel while we were preparing the requested block
            if (!cancelledPeerRequests.remove(Mapper.mapper().buildKey(pieceIndex, offset, length))) {
                try {
                    connection.postMessage(new Piece(pieceIndex, offset, block.getBlock()));
                } catch (InvalidMessageException e) {
                    throw new BtException("Failed to send PIECE", e);
                }
            }
        }

        if (requestQueue.isEmpty()) {
            if (currentPiece.isPresent()) {
                if (pieceManager.checkPieceCompleted(currentPiece.get())) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Finished downloading piece #" + currentPiece.get() +
                                "; peer: " + connection.getRemotePeer());
                    }
                    currentPiece = Optional.empty();
                    pendingWrites.clear();
                }
                // TODO: what if peer just doesn't respond? or if some block writes have failed?
                // being overly optimistical here, need to add some fallback strategy to restart piece
                // (prob. with another peer, i.e. in another conn worker)
                if (connectionState.isPeerChoking()) {
                    pieceManager.unselectPieceForPeer(connection, currentPiece.get());
                    currentPiece = Optional.empty();
                    requestQueue.clear();
                    pendingRequests.clear();
                }
            } else {
                if (pieceManager.piecesLeft() > 0 && pieceManager.mightSelectPieceForPeer(connection)) {
                    if (!connectionState.isInterested()) {
                        connection.postMessage(Interested.instance());
                        connectionState.setInterested(true);
                    }

                } else if (connectionState.isInterested()) {
                    connection.postMessage(NotInterested.instance());
                    connectionState.setInterested(false);
                }
            }
        }

        if (!connectionState.isPeerChoking()) {
            if (!currentPiece.isPresent()) {
                Optional<Integer> nextPiece = pieceManager.selectPieceForPeer(connection);
                if (nextPiece.isPresent()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Begin downloading piece #" + nextPiece.get() +
                                "; peer: " + connection.getRemotePeer());
                    }
                    currentPiece = nextPiece;
                    requestQueue.addAll(pieceManager.buildRequestsForPiece(nextPiece.get()));
                    lastBuiltRequests = System.currentTimeMillis();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Initializing request queue {piece #" + nextPiece.get() +
                                ", total requests: " + requestQueue.size() + "}");
                    }
                }
            } else if (requestQueue.isEmpty() && (System.currentTimeMillis() - lastBuiltRequests) >= 30000) {
                // this may happen when some of the received blocks were discarded by the data worker;
                // here we again create requests for the missing blocks;
                // consider this to be a kind of tradeoff between memory consumption
                // (internal capacity of the data worker) and additional network overhead from the duplicate requests
                // while ensuring that the piece WILL be downloaded eventually
                // TODO: in future this should be handled more intelligently by dynamic load balancing
                requestQueue.addAll(
                        pieceManager.buildRequestsForPiece(currentPiece.get()).stream()
                            .filter(request -> {
                                Object key = Mapper.mapper().buildKey(
                                    request.getPieceIndex(), request.getOffset(), request.getLength());
                                if (pendingRequests.contains(key)) {
                                    return false;
                                }

                                BlockWrite blockWrite = pendingWrites.get(key);
                                if (blockWrite == null) {
                                    return true;
                                }

                                boolean failed = blockWrite.isComplete() && !blockWrite.isSuccess();
                                if (failed) {
                                    pendingWrites.remove(key);
                                }
                                return failed;

                            }).collect(Collectors.toList()));

                lastBuiltRequests = System.currentTimeMillis();

                if (LOGGER.isTraceEnabled() && !requestQueue.isEmpty()) {
                    LOGGER.trace("Re-initializing request queue {piece #" + currentPiece.get() +
                            ", total requests: " + requestQueue.size() + "}");
                }
            }
            while (!requestQueue.isEmpty() && pendingRequests.size() <= MAX_PENDING_REQUESTS) {
                Request request = requestQueue.poll();
                Object key = Mapper.mapper().buildKey(
                            request.getPieceIndex(), request.getOffset(), request.getLength());
                if (!pendingRequests.contains(key)) {
                    connection.postMessage(request);
                    pendingRequests.add(key);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "Worker {peer: " + connection.getRemotePeer() + "}";
    }

    static class ConnectionState implements IConnectionState {

        private static final Duration CHOKING_THRESHOLD = Duration.ofMillis(10000);

        private boolean interested;
        private boolean peerInterested;
        private boolean choking;
        private boolean peerChoking;

        private Optional<Boolean> shouldChoke;

        private long lastChoked;

        ConnectionState() {
            choking = true;
            peerChoking = true;
            shouldChoke = Optional.empty();
        }

        @Override
        public boolean isInterested() {
            return interested;
        }

        @Override
        public void setInterested(boolean interested) {
            this.interested = interested;
        }

        @Override
        public boolean isPeerInterested() {
            return peerInterested;
        }

        @Override
        public void setPeerInterested(boolean peerInterested) {
            this.peerInterested = peerInterested;
        }

        @Override
        public boolean isChoking() {
            return choking;
        }

        @Override
        public void setChoking(boolean shouldChoke) {
            this.shouldChoke = Optional.of(shouldChoke);
        }

        @Override
        public boolean isPeerChoking() {
            return peerChoking;
        }

        @Override
        public void setPeerChoking(boolean peerChoking) {
            this.peerChoking = peerChoking;
        }

        void updateConnection(IPeerConnection connection) {

            if (!shouldChoke.isPresent()) {
                if (peerInterested && choking) {
                    if (mightUnchoke()) {
                        shouldChoke = Optional.of(Boolean.FALSE); // should unchoke
                    }
                } else if (!peerInterested && !choking) {
                    shouldChoke = Optional.of(Boolean.TRUE);
                }
            }

            shouldChoke.ifPresent(shouldChoke -> {
                if (shouldChoke != choking) {
                    if (shouldChoke) {
                        // choke immediately
                        choking = true;
                        connection.postMessage(Choke.instance());
                        lastChoked = System.currentTimeMillis();
                    } else if (mightUnchoke()) {
                        choking = false;
                        connection.postMessage(Unchoke.instance());
                    }
                }
                this.shouldChoke = Optional.empty();
            });
        }

        private boolean mightUnchoke() {
            // unchoke depending on last choked time to avoid fibrillation
            return System.currentTimeMillis() - lastChoked >= CHOKING_THRESHOLD.toMillis();
        }
    }
}
