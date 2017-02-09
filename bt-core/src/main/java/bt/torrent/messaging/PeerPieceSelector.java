package bt.torrent.messaging;

import bt.net.Peer;
import bt.torrent.Bitfield;
import bt.torrent.Bitfield.PieceStatus;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.PieceSelector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class PeerPieceSelector {

    private BitfieldBasedStatistics pieceStatistics;
    private PieceSelector selector;
    private Bitfield localBitfield;
    private Map<Peer, Bitfield> peerBitfields;

    PeerPieceSelector(Bitfield localBitfield, BitfieldBasedStatistics pieceStatistics, PieceSelector selector) {
        this.pieceStatistics = pieceStatistics;
        this.selector = selector;
        this.localBitfield = localBitfield;
        this.peerBitfields = new HashMap<>();
    }

    void addPeerBitfield(Peer peer, Bitfield peerBitfield) {
        peerBitfields.put(peer, peerBitfield);
        pieceStatistics.addBitfield(peerBitfield);
    }

    void removePeerBitfield(Peer peer) {
        pieceStatistics.removeBitfield(peerBitfields.get(peer));
    }

    void addPeerPiece(Peer peer, Integer pieceIndex) {
        Bitfield peerBitfield = peerBitfields.get(peer);
        if (peerBitfield == null) {
            peerBitfield = new Bitfield(localBitfield.getPiecesTotal());
            peerBitfields.put(peer, peerBitfield);
        }

        peerBitfield.markComplete(pieceIndex);
        pieceStatistics.addPiece(pieceIndex);
    }

    Optional<Integer> selectPieceForPeer(Peer peer) {
        Bitfield peerBitfield = peerBitfields.get(peer);
        if (peerBitfield != null) {
            List<Integer> pieces = selector.getNextPieces()
                    .filter(piece -> peerBitfield.getPieceStatus(piece) == PieceStatus.COMPLETE_VERIFIED)
                    .limit(1)
                    .collect(Collectors.toList());
            if (pieces.size() > 0) {
                return Optional.of(pieces.get(0));
            }
        }
        return Optional.empty();
    }
}
