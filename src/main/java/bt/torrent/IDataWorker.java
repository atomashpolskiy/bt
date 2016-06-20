package bt.torrent;

import bt.net.Peer;

public interface IDataWorker extends Runnable {

    boolean addBlockRequest(Peer peer, int pieceIndex, int offset, int length);

    BlockWrite addBlock(Peer peer, int pieceIndex, int offset, byte[] block);

    BlockRead getCompletedBlockRequest(Peer peer);

    void shutdown();
}
