package bt.torrent.messaging;

import bt.net.Peer;
import bt.torrent.Bitfield;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

class Assignments {

    private static final Logger LOGGER = LoggerFactory.getLogger(Assignments.class);

    private Bitfield bitfield;
    private Map<Peer, LinkedList<Integer>> assignments;
    private Map<Integer, Set<Peer>> assignees;
    private Map<Integer, Peer> claimedPieces;

    private Random random;

    Assignments(Bitfield bitfield) {
        this.bitfield = bitfield;
        this.assignments = new HashMap<>();
        this.assignees = new HashMap<>();
        this.claimedPieces = new HashMap<>();
        this.random = new Random(System.currentTimeMillis());
    }

    void assignPiece(Peer peer, Integer pieceIndex) {
        // avoid duplicate assignments
        if (getOrCreateAssignees(pieceIndex).add(peer)) {
            getOrCreateAssignments(peer).add(pieceIndex);
        }
    }

    private Queue<Integer> getOrCreateAssignments(Peer peer) {
        LinkedList<Integer> pieces = assignments.get(peer);
        if (pieces == null) {
            pieces = new LinkedList<>();
            assignments.put(peer, pieces);
        }
        return pieces;
    }

    private Set<Peer> getOrCreateAssignees(Integer pieceIndex) {
        Set<Peer> peers = assignees.get(pieceIndex);
        if (peers == null) {
            peers = new HashSet<>();
            assignees.put(pieceIndex, peers);
        }
        return peers;
    }

    Optional<Integer> pollNextAssignment(Peer peer) {
        LinkedList<Integer> peerAssignments = assignments.get(peer);
        if (peerAssignments == null || peerAssignments.isEmpty()) {
            return Optional.empty();
        }

        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        boolean endgame = bitfield.getPiecesRemaining() <= getClaimedPiecesCount();

        StringBuilder buf = LOGGER.isTraceEnabled() ? new StringBuilder() : null;
        if (LOGGER.isTraceEnabled()) {
            buf.append("Trying to claim next assignment for peer ");
            buf.append(peer);
            buf.append(". Number of remaining pieces: ");
            buf.append(bitfield.getPiecesRemaining());
            buf.append(", number of pieces in progress: ");
            buf.append(getClaimedPiecesCount());
            buf.append(", endgame: " + endgame);
            buf.append(". ");
        }

        Optional<Integer> nextAssignment;
        // and should not remove this assignment from the other peers
        if (endgame) {
            // take random piece to minimize number of pieces
            // requested from different peers at the same time
            Integer pieceIndex = peerAssignments.remove(random.nextInt(peerAssignments.size()));
            nextAssignment = Optional.of(pieceIndex);
        } else {

            Integer pieceIndex;
            boolean claimed;
            do {
                pieceIndex = peerAssignments.remove();
                claimed = getClaimedBy(pieceIndex).isPresent();
                if (LOGGER.isTraceEnabled()) {
                    buf.append("Checking next piece in queue: {" + pieceIndex + "}; claimed by peer: " + getClaimedBy(pieceIndex).orElse(null) + ". ");
                }
            } while (!endgame && claimed && !peerAssignments.isEmpty());

            if (!claimed) {
                claimPiece(peer, pieceIndex);
            }
            nextAssignment = (!claimed || endgame) ? Optional.of(pieceIndex) : Optional.empty();
        }

        if (LOGGER.isTraceEnabled()) {
            if (nextAssignment.isPresent()) {
                buf.append(" => Claiming piece #");
                buf.append(nextAssignment.get());
                buf.append(" for current peer");
            } else {
                buf.append(" => No pieces to claim.");
            }
            LOGGER.trace(buf.toString());
        }

        return nextAssignment;
    }

    boolean hasAssignments(Peer peer) {
        Queue<Integer> peerAssignments = assignments.get(peer);
        return peerAssignments != null && !peerAssignments.isEmpty();
    }

    int getAssigneesCount() {
        return assignments.size();
    }

    int getAssignmentCount(Peer peer) {
        Queue<Integer> peerAssignments = assignments.get(peer);
        return (peerAssignments == null) ? 0 : peerAssignments.size();
    }

    void removeAssignees(Integer pieceIndex) {
        unclaimPiece(pieceIndex);
        Set<Peer> pieceAssignees = assignees.remove(pieceIndex);
        if (pieceAssignees != null) {
            pieceAssignees.forEach(assignee -> removeAssignment(assignee, pieceIndex));
        }
    }

    private void removeAssignment(Peer assignee, Integer assignment) {
        Queue<Integer> peerAssignments = this.assignments.get(assignee);
        if (peerAssignments != null) {
            peerAssignments.remove(assignment);
            if (peerAssignments.isEmpty()) {
                this.assignments.remove(assignee);
            }
        }
    }

    void removeAssignments(Peer peer) {
        Queue<Integer> peerAssignments = assignments.remove(peer);
        if (peerAssignments != null) {
            peerAssignments.forEach(assignment -> {
                removeAssignee(assignment, peer);
            });
        }
    }

    private void removeAssignee(Integer assignment, Peer assignee) {
        Set<Peer> pieceAssignees = assignees.get(assignment);
        if (pieceAssignees != null) {
            pieceAssignees.remove(assignee);
            if (pieceAssignees.isEmpty()) {
                assignees.remove(assignment);
                unclaimPiece(assignment);
            } else {
                Optional<Peer> claimedBy = getClaimedBy(assignment);
                if (claimedBy.isPresent() && assignee.equals(claimedBy.get())) {
                    unclaimPiece(assignment);
                }
            }
        }
    }

    private void claimPiece(Peer peer, Integer pieceIndex) {
        claimedPieces.put(pieceIndex, peer);
    }

    private Optional<Peer> getClaimedBy(Integer pieceIndex) {
        return Optional.ofNullable(claimedPieces.get(pieceIndex));
    }

    private void unclaimPiece(Integer pieceIndex) {
        claimedPieces.remove(pieceIndex);
    }

    private int getClaimedPiecesCount() {
        return claimedPieces.size();
    }
}
