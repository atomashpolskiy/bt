package bt.torrent;

import bt.data.Bitfield;
import bt.data.Bitfield.PieceStatus;
import bt.net.Peer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides piece statistics based on peer bitfields
 *
 * @since 1.0
 */
public class BitfieldBasedStatistics implements PieceStatistics {

    private final Bitfield localBitfield;
    private final Map<Peer, Bitfield> peerBitfields;
    private final int[] pieceTotals;

    public BitfieldBasedStatistics(Bitfield localBitfield) {
        this.localBitfield = localBitfield;
        this.peerBitfields = new ConcurrentHashMap<>();
        this.pieceTotals = new int[localBitfield.getPiecesTotal()];
    }

    public void addBitfield(Peer peer, Bitfield bitfield) {
        validateBitfieldLength(bitfield);
        peerBitfields.put(peer, bitfield);

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                incrementPieceTotal(i);
            }
        }
    }

    private synchronized void incrementPieceTotal(int i) {
        pieceTotals[i]++;
    }

    public void removeBitfield(Peer peer) {
        Bitfield bitfield = peerBitfields.remove(peer);
        if (bitfield == null) {
            return;
        }

        for (int i = 0; i < pieceTotals.length; i++) {
            if (bitfield.getPieceStatus(i) == PieceStatus.COMPLETE_VERIFIED) {
                decrementPieceTotal(i);
            }
        }
    }

    private synchronized void decrementPieceTotal(int i) {
        pieceTotals[i]--;
    }

    private void validateBitfieldLength(Bitfield bitfield) {
        if (bitfield.getPiecesTotal() != pieceTotals.length) {
            throw new IllegalArgumentException("Bitfield has invalid length (" + bitfield.getPiecesTotal() +
                    "). Expected number of pieces: " + pieceTotals.length);
        }
    }

    public void addPiece(Peer peer, Integer pieceIndex) {
        Bitfield bitfield = peerBitfields.get(peer);
        if (bitfield == null) {
            bitfield = new Bitfield(localBitfield.getPiecesTotal());
            Bitfield existing = peerBitfields.putIfAbsent(peer, bitfield);
            if (existing != null) {
                bitfield = existing;
            }
        }

        bitfield.markVerified(pieceIndex);
        incrementPieceTotal(pieceIndex);
    }

    public Optional<Bitfield> getPeerBitfield(Peer peer) {
        return Optional.ofNullable(peerBitfields.get(peer));
    }

    @Override
    public synchronized int getCount(int pieceIndex) {
        return pieceTotals[pieceIndex];
    }

    @Override
    public int getPiecesTotal() {
        return pieceTotals.length;
    }
}
