package bt.torrent;

import bt.torrent.Bitfield.PieceStatus;

class BitfieldBasedStatistics implements PieceStatistics {

    private int[] pieceTotals;

    BitfieldBasedStatistics(int piecesTotal) {
        this.pieceTotals = new int[piecesTotal];
    }

    void addBitfield(Bitfield bitfield) {
        validateBitfieldLength(bitfield);

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                pieceTotals[i]++;
            }
        }
    }

    void removeBitfield(Bitfield bitfield) {
        validateBitfieldLength(bitfield);

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                pieceTotals[i]--;
            }
        }
    }

    private void validateBitfieldLength(Bitfield bitfield) {
        if (bitfield.getPiecesTotal() != pieceTotals.length) {
            throw new IllegalArgumentException("Bitfield has invalid length (" + bitfield.getPiecesTotal() +
                    "). Expected number of pieces: " + pieceTotals.length);
        }
    }

    void addPiece(Integer pieceIndex) {
        pieceTotals[pieceIndex]++;
    }

    @Override
    public int getCount(int pieceIndex) {
        return pieceTotals[pieceIndex];
    }

    @Override
    public int getPiecesTotal() {
        return pieceTotals.length;
    }
}
