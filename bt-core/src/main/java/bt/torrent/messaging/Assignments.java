package bt.torrent.messaging;

import bt.net.Peer;
import bt.torrent.Bitfield;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

class Assignments {

    private Bitfield bitfield;
    private Map<Peer, Queue<Integer>> assignments;
    private Map<Integer, Set<Peer>> assignees;

    Assignments(Bitfield bitfield) {
        this.bitfield = bitfield;
        this.assignments = new HashMap<>();
        this.assignees = new HashMap<>();
    }

    void assignPiece(Peer peer, Integer pieceIndex) {
        // avoid duplicate assignments
        if (getOrCreateAssignees(pieceIndex).add(peer)) {
            getOrCreateAssignments(peer).add(pieceIndex);
        }
    }

    private Queue<Integer> getOrCreateAssignments(Peer peer) {
        Queue<Integer> assignments = this.assignments.get(peer);
        if (assignments == null) {
            assignments = new LinkedList<>();
            this.assignments.put(peer, assignments);
        }
        return assignments;
    }

    private Set<Peer> getOrCreateAssignees(Integer pieceIndex) {
        Set<Peer> assignees = this.assignees.get(pieceIndex);
        if (assignees == null) {
            assignees = new HashSet<>();
            this.assignees.put(pieceIndex, assignees);
        }
        return assignees;
    }

    Optional<Integer> pollNextAssignment(Peer peer) {
        Queue<Integer> peerAssignments = assignments.get(peer);
        if (peerAssignments == null || peerAssignments.isEmpty()) {
            return Optional.empty();
        }

        Integer pieceIndex = peerAssignments.remove();
        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        // and should not remove this assignment from the other peers
        if (bitfield.getPiecesRemaining() > assignees.size()) {
            removeAssignees(pieceIndex, Collections.singleton(peer));
        }
        return Optional.of(pieceIndex);
    }

    boolean hasAssignments(Peer peer) {
        Queue<Integer> peerAssignments = assignments.get(peer);
        return peerAssignments != null && !peerAssignments.isEmpty();
    }

    boolean hasAssignees(Integer pieceIndex) {
        Set<Peer> pieceAssignees = assignees.get(pieceIndex);
        return pieceAssignees != null && !pieceAssignees.isEmpty();
    }

    void removeAssignees(Integer pieceIndex) {
        removeAssignees(pieceIndex, Collections.emptySet());
    }

    private void removeAssignees(Integer pieceIndex, Set<Peer> exceptThese) {
        Set<Peer> pieceAssignees = exceptThese.isEmpty() ? assignees.remove(pieceIndex) : assignees.get(pieceIndex);
        if (pieceAssignees != null) {
            Iterator<Peer> iter = pieceAssignees.iterator();
            while (iter.hasNext()) {
                Peer assignee = iter.next();
                if (!exceptThese.contains(assignee)) {
                    removeAssignment(assignee, pieceIndex);
                    iter.remove();
                }
            }
            if (pieceAssignees.isEmpty()) {
                assignees.remove(pieceIndex);
            }
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
            }
        }
    }

    int getAssigneesCount() {
        return assignments.size();
    }

    int getAssignmentCount(Peer peer) {
        Queue<Integer> peerAssignments = assignments.get(peer);
        return (peerAssignments == null) ? 0 : peerAssignments.size();
    }
}
