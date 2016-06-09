package bt.torrent;

import bt.net.PeerConnection;
import bt.protocol.Request;

import java.util.List;
import java.util.Map;

public interface IPieceManager {

    boolean haveAnyData();

    byte[] getBitfield();

    void peerHasBitfield(PeerConnection peer, byte[] peerBitfield);

    void peerHasPiece(PeerConnection peer, int pieceIndex);

    boolean checkPieceCompleted(int pieceIndex);

    Map<Integer, List<PeerConnection>> getNextPieces(int limit);

    List<Request> buildRequestsForPiece(int pieceIndex);
}
