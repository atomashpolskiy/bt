package bt.torrent.messaging;

import bt.protocol.Bitfield;
import bt.protocol.Have;
import bt.torrent.IPieceManager;
import bt.torrent.annotation.Consumes;

public class BitfieldConsumer {

    private IPieceManager pieceManager;

    public BitfieldConsumer(IPieceManager pieceManager) {
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
