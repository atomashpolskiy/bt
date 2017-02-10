package bt.torrent.messaging;

import bt.torrent.Bitfield;

import java.util.function.Predicate;

class IncompleteUnassignedPieceValidator implements Predicate<Integer> {

    private Bitfield bitfield;
    private Assignments assignments;

    IncompleteUnassignedPieceValidator(Bitfield bitfield, Assignments assignments) {
        this.bitfield = bitfield;
        this.assignments = assignments;
    }

    @Override
    public boolean test(Integer pieceIndex) {
        return ! (assignments.isAssigned(pieceIndex) || isComplete(pieceIndex));
    }

    private boolean isComplete(Integer pieceIndex) {
        Bitfield.PieceStatus pieceStatus = bitfield.getPieceStatus(pieceIndex);
        return pieceStatus == Bitfield.PieceStatus.COMPLETE || pieceStatus == Bitfield.PieceStatus.COMPLETE_VERIFIED;
    }
}
