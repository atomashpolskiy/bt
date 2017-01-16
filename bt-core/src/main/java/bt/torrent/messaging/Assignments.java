package bt.torrent.messaging;

import bt.net.Peer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class Assignments {

    private Map<Peer, Integer> assignedPieces;
    private Map<Integer, Peer> assignedPeers;

    Assignments() {
        assignedPieces = new HashMap<>();
        assignedPeers = new HashMap<>();
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
}
