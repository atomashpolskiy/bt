package bt.torrent.messaging.core;

import bt.protocol.Bitfield;
import bt.protocol.Have;
import bt.torrent.annotation.Consumes;
import bt.torrent.messaging.MessageContext;

/**
 * Consumes peer bitfield.
 *
 * <p>Note that the local bitfield is sent to a remote peer
 * during the connection initialization sequence.
 *
 * @see bt.net.BitfieldConnectionHandler
 * @since 1.0
 */
public class BitfieldConsumer {

    private PieceManager pieceManager;

    BitfieldConsumer(PieceManager pieceManager) {
        this.pieceManager = pieceManager;
    }

    @Consumes
    public void consume(Bitfield bitfield, MessageContext context) {
        pieceManager.peerHasBitfield(context.getPeer(),
                new bt.torrent.Bitfield(bitfield.getBitfield(), pieceManager.getBitfield().getPiecesTotal()));
    }

    @Consumes
    public void consume(Have have, MessageContext context) {
        pieceManager.peerHasPiece(context.getPeer(), have.getPieceIndex());
    }
}
