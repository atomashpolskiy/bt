package bt.torrent;

import bt.BtException;
import bt.net.PeerConnection;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionWorker.class);

    private static final int MAX_PENDING_REQUESTS = 3;

    private IPieceManager pieceManager;
    private DataWorker dataWorker;

    private final PeerConnection connection;
    private ConnectionState connectionState;

    private long received;
    private long sent;

    private Optional<Integer> currentPiece;

    private Queue<Request> requestQueue;
    private Set<Object> pendingRequests;
    private Set<Object> cancelledPeerRequests;

    ConnectionWorker(IPieceManager pieceManager, DataWorker dataWorker, PeerConnection connection) {

        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.connection = connection;
        connectionState = new ConnectionState();

        currentPiece = Optional.empty();

        requestQueue = new LinkedBlockingQueue<>();
        pendingRequests = new HashSet<>();
        cancelledPeerRequests = new HashSet<>();

        if (pieceManager.haveAnyData()) {
            Bitfield bitfield = new Bitfield(pieceManager.getBitfield());
            connection.postMessage(bitfield);
        }
    }

    public long getReceived() {
        return received;
    }

    public long getSent() {
        return sent;
    }

    public Collection<BlockWrite> doWork() {

        checkConnection();
        Collection<BlockWrite> blockWrites = processIncomingMessages();
        processOutgoingMessages();
        return blockWrites;
    }

    private void checkConnection() {
        if (connection.isClosed()) {
            throw new BtException("Connection is closed: " + connection.getRemotePeer());
        }
    }

    private Collection<BlockWrite> processIncomingMessages() {

        Collection<BlockWrite> blockWrites = Collections.emptyList();

        Message message = connection.readMessageNow();
        if (message != null) {

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received " + message + " from peer: " + connection.getRemotePeer());
            }

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
                    connection.postMessage(Choke.instance());
                    connectionState.setChoking(true);
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
                        boolean mightRead = dataWorker.addBlockRequest(connection.getRemotePeer(),
                                request.getPieceIndex(), request.getOffset(), request.getLength());

                        if (!mightRead) {
                            connection.postMessage(Choke.instance());
                            connectionState.setChoking(true);
                        }
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
                        BlockWrite blockWrite = dataWorker.addBlock(connection.getRemotePeer(), pieceIndex, offset, block);
                        if (blockWrite != null) {
                            blockWrites = Collections.singletonList(blockWrite);
                        }
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

        return blockWrites;
    }

    private void processOutgoingMessages() {

        Collection<BlockRead> completedRequests = dataWorker.getCompletedBlockRequests(connection.getRemotePeer(), 3);
        for (BlockRead block : completedRequests) {

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
                }
                // TODO: what if peer just doesn't respond? or if some block writes have failed?
                // being overly optimistical here, need to add some fallback strategy to restart piece
                // (prob. with another peer, i.e. in another conn worker)
            } else {
                if (pieceManager.mightSelectPieceForPeer(connection)) {
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
                }
            }
            while (!requestQueue.isEmpty() && pendingRequests.size() <= MAX_PENDING_REQUESTS) {
                Request request = requestQueue.poll();
                connection.postMessage(request);
                pendingRequests.add(Mapper.mapper().buildKey(
                        request.getPieceIndex(), request.getOffset(), request.getLength()));
            }
        }
    }

    @Override
    public String toString() {
        return "Worker {peer: " + connection.getRemotePeer() + "}";
    }
}
