package bt.torrent;

import bt.net.IPeerConnection;
import bt.net.Peer;
import bt.protocol.Request;

import java.util.List;
import java.util.Optional;

public interface IPieceManager {

    boolean haveAnyData();

    byte[] getBitfield();

    void peerHasBitfield(Peer peer, byte[] peerBitfield);

    void peerHasPiece(Peer peer, Integer pieceIndex);

    boolean checkPieceCompleted(Integer pieceIndex);

    boolean checkPieceVerified(Integer pieceIndex);

    boolean mightSelectPieceForPeer(Peer peer);

    Optional<Integer> selectPieceForPeer(Peer peer);

    void unselectPieceForPeer(Peer peer, Integer pieceIndex);

    List<Request> buildRequestsForPiece(Integer pieceIndex);

    int piecesLeft();
}
