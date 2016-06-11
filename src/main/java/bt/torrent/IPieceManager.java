package bt.torrent;

import bt.net.PeerConnection;
import bt.protocol.Request;

import java.util.List;
import java.util.Optional;

public interface IPieceManager {

    boolean haveAnyData();

    byte[] getBitfield();

    void peerHasBitfield(PeerConnection peer, byte[] peerBitfield);

    void peerHasPiece(PeerConnection peer, Integer pieceIndex);

    boolean checkPieceCompleted(Integer pieceIndex);

    boolean mightSelectPieceForPeer(PeerConnection peer);

    Optional<Integer> selectPieceForPeer(PeerConnection peer);

    List<Request> buildRequestsForPiece(Integer pieceIndex);

    int piecesLeft();
}
