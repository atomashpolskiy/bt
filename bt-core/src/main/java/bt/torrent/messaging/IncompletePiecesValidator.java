package bt.torrent.messaging;

import bt.data.Bitfield;

import java.util.function.Predicate;

public class IncompletePiecesValidator implements Predicate<Integer> {

    private Bitfield bitfield;

    public IncompletePiecesValidator(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    @Override
    public boolean test(Integer pieceIndex) {
        return !isComplete(pieceIndex);
    }

    private boolean isComplete(Integer pieceIndex) {
        Bitfield.PieceStatus pieceStatus = bitfield.getPieceStatus(pieceIndex);
        return pieceStatus == Bitfield.PieceStatus.COMPLETE || pieceStatus == Bitfield.PieceStatus.COMPLETE_VERIFIED;
    }
}
