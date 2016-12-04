package bt.torrent.messaging.core;

import bt.net.Peer;
import bt.torrent.Bitfield;
import bt.torrent.Bitfield.PieceStatus;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.PieceSelectionStrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

class PieceSelector {

    private BitfieldBasedStatistics stats;
    private PieceSelectionStrategy selector;
    private Predicate<Integer> validator;

    private Bitfield localBitfield;
    private Map<Peer, Bitfield> peerBitfields;

    PieceSelector(Bitfield localBitfield, PieceSelectionStrategy selector, Predicate<Integer> validator) {

        this.stats = new BitfieldBasedStatistics(localBitfield.getPiecesTotal());
        this.selector = selector;
        this.validator = validator;
        this.localBitfield = localBitfield;
        this.peerBitfields = new HashMap<>();
    }

    void addPeerBitfield(Peer peer, Bitfield peerBitfield) {
        peerBitfields.put(peer, peerBitfield);
        stats.addBitfield(peerBitfield);
    }

    void removePeerBitfield(Peer peer) {
        stats.removeBitfield(peerBitfields.get(peer));
    }

    void addPeerPiece(Peer peer, Integer pieceIndex) {
        Bitfield peerBitfield = peerBitfields.get(peer);
        if (peerBitfield == null) {
            peerBitfield = new Bitfield(localBitfield.getPiecesTotal());
            peerBitfields.put(peer, peerBitfield);
        }

        peerBitfield.markComplete(pieceIndex);
        stats.addPiece(pieceIndex);
    }

    Optional<Integer> selectPieceForPeer(Peer peer, Predicate<Integer> validator) {

        Bitfield peerBitfield = peerBitfields.get(peer);
        if (peerBitfield != null) {
            Integer[] pieces = getNextPieces();
            for (Integer piece : pieces) {
                if (peerBitfield.getPieceStatus(piece) == PieceStatus.COMPLETE_VERIFIED && validator.test(piece)) {
                    return Optional.of(piece);
                }
            }
        }
        return Optional.empty();
    }

    Integer[] getNextPieces() {
        return selector.getNextPieces(stats, peerBitfields.size(),
                pieceIndex -> (pieceIndex < stats.getPiecesTotal()) && validator.test(pieceIndex));
    }
}
