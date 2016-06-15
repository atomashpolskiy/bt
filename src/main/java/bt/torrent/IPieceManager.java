package bt.torrent;

import bt.net.IPeerConnection;
import bt.protocol.Request;

import java.util.List;
import java.util.Optional;

public interface IPieceManager {

    boolean haveAnyData();

    byte[] getBitfield();

    void peerHasBitfield(IPeerConnection peer, byte[] peerBitfield);

    void peerHasPiece(IPeerConnection peer, Integer pieceIndex);

    boolean checkPieceCompleted(Integer pieceIndex);

    boolean checkPieceVerified(Integer pieceIndex);

    boolean mightSelectPieceForPeer(IPeerConnection peer);

    Optional<Integer> selectPieceForPeer(IPeerConnection peer);

    List<Request> buildRequestsForPiece(Integer pieceIndex);

    int piecesLeft();
}
