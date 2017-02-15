package bt.torrent.messaging;

import bt.net.Peer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

class Assignments {

    private Map<Peer, Queue<Integer>> assignments;
    private Map<Integer, Set<Peer>> assignees;

    Assignments() {
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
        removeAssignees(pieceIndex, Collections.singleton(peer));
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
            pieceAssignees.forEach(assignee -> {
                if (!exceptThese.contains(assignee)) {
                    this.assignments.get(assignee).remove(pieceIndex);
                }
            });
        }
    }

    void removeAssignments(Peer peer) {
        Queue<Integer> peerAssignments = assignments.remove(peer);
        if (peerAssignments != null) {
            peerAssignments.forEach(assignment -> {
                assignees.get(assignment).remove(peer);
            });
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
