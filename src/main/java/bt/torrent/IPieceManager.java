package bt.torrent;

import bt.net.PeerConnection;
import bt.protocol.Request;

import java.util.List;

public interface IPieceManager {

    boolean haveAnyData();

    byte[] getBitfield();

    void peerHasBitfield(PeerConnection peer, byte[] peerBitfield);

    void peerHasPiece(PeerConnection peer, int pieceIndex);

    boolean checkPieceCompleted(int pieceIndex);

    int getNextPieceForPeer(PeerConnection peer);

    List<Request> buildRequestsForPiece(int pieceIndex);

    int piecesLeft();
}
