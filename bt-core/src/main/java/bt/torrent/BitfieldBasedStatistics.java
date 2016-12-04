package bt.torrent;

import bt.torrent.Bitfield.PieceStatus;

/**
 * Provides piece statistics based on peer bitfields
 *
 * @since 1.0
 */
public class BitfieldBasedStatistics implements PieceStatistics {

    private int[] pieceTotals;

    public BitfieldBasedStatistics(int piecesTotal) {
        this.pieceTotals = new int[piecesTotal];
    }

    public void addBitfield(Bitfield bitfield) {
        validateBitfieldLength(bitfield);

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                pieceTotals[i]++;
            }
        }
    }

    public void removeBitfield(Bitfield bitfield) {
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

    public void addPiece(Integer pieceIndex) {
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
