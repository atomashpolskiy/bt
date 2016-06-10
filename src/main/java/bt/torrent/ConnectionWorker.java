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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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

    private int currentPiece;

    private Queue<Request> requestQueue;
    private Set<Object> pendingRequests;
    private Set<Object> cancelledPeerRequests;

    ConnectionWorker(IPieceManager pieceManager, DataWorker dataWorker, PeerConnection connection) {

        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;

        this.connection = connection;
        connectionState = new ConnectionState();

        currentPiece = -1;

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

        Collection<BlockWrite> blockWrites = new ArrayList<>();

        Message message = connection.readMessageNow();
        if (message != null) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Received " + message + " from peer: " + connection.getRemotePeer());
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
                    pendingRequests.clear();
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
                        boolean mightRead = dataWorker.addBlockRequest(connection.getRemotePeer(), request);
                        if (!mightRead) {
                            connection.postMessage(Choke.instance());
                            connectionState.setChoking(true);
                        }
                    }
                    break;
                }
                case CANCEL: {
                    Cancel cancel = (Cancel) message;
                    cancelledPeerRequests.add(Mapper.mapper().keyForCancel(cancel));
                    break;
                }
                case PIECE: {
                    Piece piece = (Piece) message;
                    // check that this block was requested in the first place
                    Object key = Mapper.mapper().keyForPiece(piece);
                    if (!pendingRequests.remove(key)) {
                        throw new BtException("Received unexpected block " + piece + " from peer: " + connection.getRemotePeer());
                    } else {
                        blockWrites.add(dataWorker.addBlock(piece));
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
        for (BlockRead completedRequest : completedRequests) {
            Request request = completedRequest.getRequest();
            // check that peer hadn't sent cancel while we were preparing the requested block
            if (!cancelledPeerRequests.remove(Mapper.mapper().keyForRequest(request))) {
                try {
                    connection.postMessage(new Piece(request.getPieceIndex(), request.getOffset(), completedRequest.getBlock()));
                } catch (InvalidMessageException e) {
                    throw new BtException("Failed to send PIECE (built from " + request + ")", e);
                }
            }
        }

        if (requestQueue.isEmpty()) {
            if (currentPiece < 0) {
                int nextPiece = pieceManager.getNextPieceForPeer(connection);
                if (nextPiece < 0) {
                    if (connectionState.isInterested()) {
                        connection.postMessage(NotInterested.instance());
                        connectionState.setInterested(false);
                    }
                } else {
                    currentPiece = nextPiece;
                    if (!connectionState.isInterested()) {
                        connection.postMessage(Interested.instance());
                        connectionState.setInterested(true);
                    }
                    requestQueue.addAll(pieceManager.buildRequestsForPiece(nextPiece));
                }
            } else {
                if (pieceManager.checkPieceCompleted(currentPiece)) {
                    currentPiece = -1;
                }
            }
        }

        if (!connectionState.isPeerChoking()) {
            while (!requestQueue.isEmpty() && pendingRequests.size() <= MAX_PENDING_REQUESTS) {
                Request request = requestQueue.poll();
                connection.postMessage(request);
                pendingRequests.add(Mapper.mapper().keyForRequest(request));
            }
        }
    }

    @Override
    public String toString() {
        return "Worker {peer: " + connection.getRemotePeer() + "}";
    }
}
