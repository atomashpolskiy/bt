package bt.torrent.messaging;

import bt.event.EventSink;
import bt.net.Peer;
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
    private EventSink eventSink;

    public BitfieldConsumer(bt.data.Bitfield bitfield, BitfieldBasedStatistics pieceStatistics, EventSink eventSink) {
        this.bitfield = bitfield;
        this.pieceStatistics = pieceStatistics;
        this.eventSink = eventSink;
    }

    @Consumes
    public void consume(Bitfield bitfieldMessage, MessageContext context) {
        Peer peer = context.getPeer();
        bt.data.Bitfield peerBitfield = new bt.data.Bitfield(bitfieldMessage.getBitfield(), bitfield.getPiecesTotal());
        pieceStatistics.addBitfield(peer, peerBitfield);
        eventSink.firePeerBitfieldUpdated(context.getTorrentId().get(), peer, peerBitfield);
    }

    @Consumes
    public void consume(Have have, MessageContext context) {
        Peer peer = context.getPeer();
        pieceStatistics.addPiece(peer, have.getPieceIndex());
        pieceStatistics.getPeerBitfield(peer).ifPresent(
                bitfield -> eventSink.firePeerBitfieldUpdated(context.getTorrentId().get(), peer, bitfield));
    }
}
