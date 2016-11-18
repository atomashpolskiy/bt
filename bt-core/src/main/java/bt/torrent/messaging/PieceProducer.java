package bt.torrent.messaging;

import bt.BtException;
import bt.net.Peer;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.Piece;
import bt.torrent.annotation.Produces;
import bt.torrent.data.BlockRead;
import bt.torrent.data.IDataWorker;

import java.util.function.Consumer;

/**
 * Produces blocks, requested by the remote peer.
 *
 * @since 1.0
 */
public class PieceProducer {

    private IDataWorker dataWorker;

    public PieceProducer(IDataWorker dataWorker) {
        this.dataWorker = dataWorker;
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {

        Peer peer = context.getPeer();

        BlockRead block;
        while ((block = dataWorker.getCompletedBlockRequest(peer)) != null) {
            try {
                messageConsumer.accept(new Piece(block.getPieceIndex(), block.getOffset(), block.getBlock()));
            } catch (InvalidMessageException e) {
                throw new BtException("Failed to send PIECE", e);
            }
        }
    }
}
