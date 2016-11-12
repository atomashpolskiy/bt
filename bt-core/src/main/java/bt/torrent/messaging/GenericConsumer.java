package bt.torrent.messaging;

import bt.BtException;
import bt.net.Peer;
import bt.protocol.Bitfield;
import bt.protocol.Cancel;
import bt.protocol.Choke;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.KeepAlive;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Piece;
import bt.protocol.Request;
import bt.protocol.Unchoke;
import bt.torrent.IPieceManager;
import bt.torrent.data.BlockWrite;
import bt.torrent.data.IDataWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericConsumer implements MessageConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericConsumer.class);

    private IPieceManager pieceManager;
    private IDataWorker dataWorker;

    public GenericConsumer(IPieceManager pieceManager, IDataWorker dataWorker) {
        this.pieceManager = pieceManager;
        this.dataWorker = dataWorker;
    }

    @Override
    public void consume(Peer peer, ConnectionState connectionState, Message message) {

        Class<? extends Message> type = message.getClass();

        if (KeepAlive.class.equals(type)) {
            return;
        }
        if (Bitfield.class.equals(type)) {
            Bitfield bitfield = (Bitfield) message;
            pieceManager.peerHasBitfield(peer,
                    new bt.torrent.Bitfield(bitfield.getBitfield(), pieceManager.getBitfield().getPiecesTotal()));
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
                    connectionState.setShouldChoke(true);
                }
            }
            return;
        }
        if (Cancel.class.equals(type)) {
            Cancel cancel = (Cancel) message;
            connectionState.onCancel(cancel);
            return;
        }
        if (Piece.class.equals(type)) {
            Piece piece = (Piece) message;
            receivePiece(peer, connectionState, piece);
        }
    }

    public void receivePiece(Peer peer, ConnectionState connectionState, Piece piece) {

        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset();

        byte[] block = piece.getBlock();
        // check that this block was requested in the first place
        Object key = Mapper.mapper().buildKey(pieceIndex, offset, block.length);
        if (!connectionState.getPendingRequests().remove(key)) {
            throw new BtException("Received unexpected block " + piece +
                    " from peer: " + peer);
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(connectionState.getRequestQueue().size() + " requests left in queue {piece #" + pieceManager.getAssignedPiece(peer).get() + "}");
            }
            connectionState.incrementDownloaded(block.length);
            BlockWrite blockWrite = dataWorker.addBlock(peer, pieceIndex, offset, block);
            if (!blockWrite.isComplete() || blockWrite.isSuccess()) {
                connectionState.getPendingWrites().put(key, blockWrite);
            }
        }
    }
}
