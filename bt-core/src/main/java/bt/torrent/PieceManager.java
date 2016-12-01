package bt.torrent;

import bt.BtException;
import bt.net.Peer;
import bt.torrent.Bitfield.PieceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Predicate;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PieceManager implements IPieceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PieceManager.class);

    private Bitfield localBitfield;
    private PieceSelector selector;
    private Assignments assignments;
    private Predicate<Integer> selectionValidator;

    public PieceManager(Bitfield localBitfield, PieceSelectionStrategy pieceSelectionStrategy) {
        this.localBitfield = localBitfield;

        this.selector = new PieceSelector(localBitfield, pieceSelectionStrategy,
                pieceIndex -> !checkPieceCompleted(pieceIndex));

        this.assignments = new Assignments();
        this.selectionValidator = pieceIndex -> !assignments.getAssignee(pieceIndex).isPresent();
    }

    @Override
    public Bitfield getBitfield() {
        return localBitfield;
    }

    // TODO: Check if peer has everything (i.e. is a seeder) and store him separately
    // this should help to improve performance of the next piece selection algorithm
    @Override
    public void peerHasBitfield(Peer peer, Bitfield peerBitfield) {
        if (peerBitfield.getPiecesTotal() == localBitfield.getPiecesTotal()) {
            selector.addPeerBitfield(peer, peerBitfield);
        } else {
            throw new BtException("bitfield has wrong size: " + peerBitfield.getPiecesTotal() +
                    ". Expected: " + localBitfield.getPiecesTotal());
        }
    }

    @Override
    public void peerHasPiece(Peer peer, Integer pieceIndex) {
        validatePieceIndex(pieceIndex);
        selector.addPeerPiece(peer, pieceIndex);
    }

    @Override
    public boolean checkPieceCompleted(Integer pieceIndex) {
        return checkPieceCompleted(pieceIndex, false);
    }

    @Override
    public boolean checkPieceVerified(Integer pieceIndex) {
        return checkPieceCompleted(pieceIndex, true);
    }

    // TODO: problem with concurrent completePieces update in case chunk.getStatus == VERIFIED
    private synchronized boolean checkPieceCompleted(Integer pieceIndex, boolean verifiedOnly) {
        boolean completed = false;
        try {
            PieceStatus pieceStatus = localBitfield.getPieceStatus(pieceIndex);
            switch (pieceStatus) {
                case COMPLETE_VERIFIED: {
                    completed = true;
                    break;
                }
                case COMPLETE: {
                    completed = !verifiedOnly;
                    break;
                }
                default: {
                    completed = false;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check if chunk is complete {piece index: " + pieceIndex + "}", e);
        }

        if (completed && assignments.getAssignee(pieceIndex).isPresent()) {
            assignments.removeAssignee(pieceIndex);
        }
        return completed;
    }

    @Override
    public boolean mightSelectPieceForPeer(Peer peer) {
        if (assignments.getAssignedPiece(peer).isPresent()) {
            return false;
        }
        Optional<Integer> piece = selector.selectPieceForPeer(peer, selectionValidator);
        return piece.isPresent() && !assignments.getAssignee(piece.get()).isPresent();
    }

    @Override
    public Optional<Integer> selectPieceForPeer(Peer peer) {
        Optional<Integer> assignedPiece = assignments.getAssignedPiece(peer);
        return assignedPiece.isPresent()? assignedPiece : selectAndAssignPiece(peer);
    }

    @Override
    public void unselectPieceForPeer(Peer peer, Integer pieceIndex) {
        Integer assignedPieceIndex = assignments.getAssignedPiece(peer)
                .orElseThrow(() -> new BtException("Peer " + peer + " is not assigned to any piece"));
        if (!assignedPieceIndex.equals(pieceIndex)) {
            throw new BtException("Peer " + peer + " is not assigned to piece #" + pieceIndex);
        }
        assignments.removeAssignment(peer);
        selector.removePeerBitfield(peer);
    }

    @Override
    public Optional<Integer> getAssignedPiece(Peer peer) {
        return assignments.getAssignedPiece(peer);
    }

    private Optional<Integer> selectAndAssignPiece(Peer peer) {
        Optional<Integer> piece = selector.selectPieceForPeer(peer, selectionValidator);
        if (piece.isPresent()) {
            assignments.assignPiece(peer, piece.get());
        }
        return piece;
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= localBitfield.getPiecesTotal()) {
            throw new BtException("Illegal piece index: " + pieceIndex);
        }
    }
}
