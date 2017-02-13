package bt.torrent.messaging;

import bt.net.Peer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class Assignments {

    private Map<Peer, Integer> assignedPieces;
    private Map<Integer, Peer> assignedPeers;

    Assignments() {
        assignedPieces = new ConcurrentHashMap<>();
        assignedPeers = new ConcurrentHashMap<>();
    }

    void assignPiece(Peer peer, Integer pieceIndex) {
        assignedPieces.put(peer, pieceIndex);
        assignedPeers.put(pieceIndex, peer);
    }

    Optional<Integer> getAssignedPiece(Peer peer) {
        return Optional.ofNullable(assignedPieces.get(peer));
    }

    Optional<Peer> getAssignee(Integer pieceIndex) {
        return Optional.ofNullable(assignedPeers.get(pieceIndex));
    }

    boolean hasAssignedPiece(Peer peer) {
        return assignedPieces.containsKey(peer);
    }

    boolean isAssigned(Integer pieceIndex) {
        return assignedPeers.containsKey(pieceIndex);
    }

    void removeAssignee(Integer pieceIndex) {
        Peer assignee = assignedPeers.remove(pieceIndex);
        if (assignee != null) {
            assignedPieces.remove(assignee);
        }
    }

    void removeAssignment(Peer peer) {
        Integer assignedPiece = assignedPieces.remove(peer);
        if (assignedPiece != null) {
            assignedPeers.remove(assignedPiece);
        }
    }

    Set<Peer> getAssignees() {
        return assignedPieces.keySet();
    }
}
