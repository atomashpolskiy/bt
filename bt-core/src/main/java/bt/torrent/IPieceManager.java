package bt.torrent;

import bt.net.Peer;
import java.util.Optional;

public interface IPieceManager {

    Bitfield getBitfield();

    void peerHasBitfield(Peer peer, Bitfield peerBitfield);

    void peerHasPiece(Peer peer, Integer pieceIndex);

    boolean checkPieceCompleted(Integer pieceIndex);

    boolean checkPieceVerified(Integer pieceIndex);

    boolean mightSelectPieceForPeer(Peer peer);

    Optional<Integer> selectPieceForPeer(Peer peer);

    void unselectPieceForPeer(Peer peer, Integer pieceIndex);

    Optional<Integer> getAssignedPiece(Peer peer);
}
