package bt.torrent.messaging;

import bt.BtException;
import bt.net.Peer;
import bt.protocol.Piece;
import bt.torrent.annotation.Consumes;
import bt.torrent.data.BlockWrite;
import bt.torrent.data.IDataWorker;

public class PieceConsumer {

    private IDataWorker dataWorker;

    public PieceConsumer(IDataWorker dataWorker) {
        this.dataWorker = dataWorker;
    }

    @Consumes
    public void consume(Piece piece, MessageContext context) {

        Peer peer = context.getPeer();
        ConnectionState connectionState = context.getConnectionState();

        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset();

        byte[] block = piece.getBlock();
        // check that this block was requested in the first place
        Object key = Mapper.mapper().buildKey(pieceIndex, offset, block.length);
        if (!connectionState.getPendingRequests().remove(key)) {
            throw new BtException("Received unexpected block " + piece +
                    " from peer: " + peer);
        } else {
            connectionState.incrementDownloaded(block.length);
            BlockWrite blockWrite = dataWorker.addBlock(peer, pieceIndex, offset, block);
            if (!blockWrite.isComplete() || blockWrite.isSuccess()) {
                connectionState.getPendingWrites().put(key, blockWrite);
            }
        }
    }
}
