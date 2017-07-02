package bt.torrent.messaging;

import bt.protocol.Bitfield;
import bt.protocol.Have;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.annotation.Consumes;

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

    private bt.data.Bitfield bitfield;
    private BitfieldBasedStatistics pieceStatistics;

    public BitfieldConsumer(bt.data.Bitfield bitfield, BitfieldBasedStatistics pieceStatistics) {
        this.bitfield = bitfield;
        this.pieceStatistics = pieceStatistics;
    }

    @Consumes
    public void consume(Bitfield bitfieldMessage, MessageContext context) {
        bt.data.Bitfield peerBitfield = new bt.data.Bitfield(bitfieldMessage.getBitfield(), bitfield.getPiecesTotal());
        pieceStatistics.addBitfield(context.getPeer(), peerBitfield);
    }

    @Consumes
    public void consume(Have have, MessageContext context) {
        pieceStatistics.addPiece(context.getPeer(), have.getPieceIndex());
    }
}
