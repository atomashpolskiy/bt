package bt.torrent;

import bt.net.Peer;

import java.util.function.Consumer;

public interface IDataWorker extends Runnable {

    void addVerifiedPieceListener(Consumer<Integer> listener);

    boolean addBlockRequest(Peer peer, int pieceIndex, int offset, int length);

    BlockWrite addBlock(Peer peer, int pieceIndex, int offset, byte[] block);

    BlockRead getCompletedBlockRequest(Peer peer);
}
