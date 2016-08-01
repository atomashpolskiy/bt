package bt.torrent;

import bt.BtException;
import bt.net.Peer;
import bt.protocol.Bitfield;
import bt.protocol.Cancel;
import bt.protocol.Choke;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.InvalidMessageException;
import bt.protocol.KeepAlive;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Piece;
import bt.protocol.Request;
import bt.protocol.Unchoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConnectionWorker implements Consumer<Message>, Supplier<Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionWorker.class);

    private static final int MAX_PENDING_REQUESTS = 3;

    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    private final Peer peer;
    private ConnectionState connectionState;

    private long lastBuiltRequests;

    private Optional<Integer> currentPiece;

    private Queue<Request> requestQueue;
    private Set<Object> pendingRequests;
    private Map<Object, BlockWrite> pendingWrites;
    private Set<Object> cancelledPeerRequests;

    private Deque<Message> outgoingMessages;

    private volatile long downloaded;
    private volatile long uploaded;

    ConnectionWorker(Peer peer, IPieceManager pieceManager, IDataWorker dataWorker) {

        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.peer = peer;
        connectionState = new ConnectionState();

        currentPiece = Optional.empty();

        requestQueue = new LinkedBlockingQueue<>();
        pendingRequests = new HashSet<>();
        pendingWrites = new HashMap<>();
        cancelledPeerRequests = new HashSet<>();

        outgoingMessages = new LinkedBlockingDeque<>();

        // TODO: this is temporary fix, need to incorporate bitfield sending into the connection initialization chain
        if (pieceManager.haveAnyData()) {
            Bitfield bitfield = new Bitfield(pieceManager.getBitfield());
            outgoingMessages.add(bitfield);
        }
    }

    // TODO: this is a hack, remove
    public void addMessage(Message message) {
        outgoingMessages.addFirst(message);
    }
    public void shutdown() {
        if (currentPiece.isPresent()) {
            pieceManager.unselectPieceForPeer(peer, currentPiece.get());
        }
    }

    @Override
    public void accept(Message message) {
        Class<? extends Message> type = message.getClass();

        if (KeepAlive.class.equals(type)) {
            return;
        }
        if (Bitfield.class.equals(type)) {
            Bitfield bitfield = (Bitfield) message;
            pieceManager.peerHasBitfield(peer, bitfield.getBitfield());
            return;
        }
        if (Choke.class.equals(type)) {
            connectionState.setPeerChoking(true);
            return;
        }
        if (Unchoke.class.equals(type)) {
            connectionState.setPeerChoking(false);
            return;
        }
        if (Interested.class.equals(type)) {
            connectionState.setPeerInterested(true);
            return;
        }
        if (NotInterested.class.equals(type)) {
            connectionState.setPeerInterested(false);
            return;
        }
        if (Have.class.equals(type)) {
            Have have = (Have) message;
            pieceManager.peerHasPiece(peer, have.getPieceIndex());
            return;
        }
        if (Request.class.equals(type)) {
            if (!connectionState.isChoking()) {
                Request request = (Request) message;
                if (!dataWorker.addBlockRequest(peer,
                            request.getPieceIndex(), request.getOffset(), request.getLength())) {
                    connectionState.setChoking(true);
                }
            }
            return;
        }
        if (Cancel.class.equals(type)) {
            Cancel cancel = (Cancel) message;
            cancelledPeerRequests.add(Mapper.mapper().buildKey(
            cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength()));
            return;
        }
        if (Piece.class.equals(type)) {
            Piece piece = (Piece) message;

            int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset();
            byte[] block = piece.getBlock();
            // check that this block was requested in the first place
            Object key = Mapper.mapper().buildKey(pieceIndex, offset, block.length);
            if (!pendingRequests.remove(key)) {
                throw new BtException("Received unexpected block " + piece +
                        " from peer: " + peer);
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(requestQueue.size() + " requests left in queue {piece #" + currentPiece.get() + "}");
                }
                downloaded += block.length;
                BlockWrite blockWrite = dataWorker.addBlock(peer, pieceIndex, offset, block);
                if (!blockWrite.isComplete() || blockWrite.isSuccess()) {
                    pendingWrites.put(key, blockWrite);
                }
            }
        }

        connectionState.updateConnection();
    }

    @Override
    public Message get() {

        if (outgoingMessages.isEmpty()) {
            prepareOutgoingMessages();
        }
        return outgoingMessages.poll();
    }

    public long getDownloaded() {
        return downloaded;
    }

    public long getUploaded() {
        return uploaded;
    }

    private void prepareOutgoingMessages() {

        BlockRead block;
        while ((block = dataWorker.getCompletedBlockRequest(peer)) != null) {
            int pieceIndex = block.getPieceIndex(),
                offset = block.getOffset(),
                length = block.getLength();

            // check that peer hadn't sent cancel while we were preparing the requested block
            if (!cancelledPeerRequests.remove(Mapper.mapper().buildKey(pieceIndex, offset, length))) {
                try {
                    uploaded += length;
                    outgoingMessages.add(new Piece(pieceIndex, offset, block.getBlock()));
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
                                "; peer: " + peer);
                    }
                    currentPiece = Optional.empty();
                    pendingWrites.clear();
                }
                // TODO: what if peer just doesn't respond? or if some block writes have failed?
                // being overly optimistical here, need to add some fallback strategy to restart piece
                // (prob. with another peer, i.e. in another conn worker)
                if (connectionState.isPeerChoking()) {
                    pieceManager.unselectPieceForPeer(peer, currentPiece.get());
                    currentPiece = Optional.empty();
                    requestQueue.clear();
                    pendingRequests.clear();
                }
            } else {
                if (pieceManager.getPiecesRemaining() > 0 && pieceManager.mightSelectPieceForPeer(peer)) {
                    if (!connectionState.isInterested()) {
                        outgoingMessages.add(Interested.instance());
                        connectionState.setInterested(true);
                    }

                } else if (connectionState.isInterested()) {
                    outgoingMessages.add(NotInterested.instance());
                    connectionState.setInterested(false);
                }
            }
        }

        if (!connectionState.isPeerChoking()) {
            if (!currentPiece.isPresent()) {
                Optional<Integer> nextPiece = pieceManager.selectPieceForPeer(peer);
                if (nextPiece.isPresent()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Begin downloading piece #" + nextPiece.get() +
                                "; peer: " + peer);
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
                    outgoingMessages.add(request);
                    pendingRequests.add(key);
                }
            }
        }

        connectionState.updateConnection();
    }

    @Override
    public String toString() {
        return "Worker {peer: " + peer + "}";
    }

    class ConnectionState implements IConnectionState {

        private final Duration CHOKING_THRESHOLD = Duration.ofMillis(10000);

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

        void updateConnection() {

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
                        outgoingMessages.addFirst(Choke.instance());
                        lastChoked = System.currentTimeMillis();
                    } else if (mightUnchoke()) {
                        choking = false;
                        outgoingMessages.addFirst(Unchoke.instance());
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

    private static class Mapper {

        private static final Mapper instance = new Mapper();

        static Mapper mapper() {
            return instance;
        }

        private Mapper() {}

        Object buildKey(int pieceIndex, int offset, int length) {
            return new Key(pieceIndex, offset, length);
        }

        private static class Key {

            private final int[] key;

            Key(int pieceIndex, int offset, int length) {
                this.key = new int[] {pieceIndex, offset, length};
            }

            int[] getKey() {
                return key;
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(key);
            }

            @Override
            public boolean equals(Object obj) {

                if (obj == null || !Key.class.equals(obj.getClass())) {
                    return false;
                }
                return (obj == this) || Arrays.equals(key, ((Key) obj).getKey());
            }
        }
    }
}
