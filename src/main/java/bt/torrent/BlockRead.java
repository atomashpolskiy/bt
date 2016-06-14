package bt.torrent;

import bt.net.Peer;

class BlockRead {

    private Peer peer;
    private int pieceIndex;
    private int offset;
    private int length;
    private byte[] block;

    BlockRead(Peer peer, int pieceIndex, int offset, int length, byte[] block) {
        this.peer = peer;
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.block = block;
    }

    Peer getPeer() {
        return peer;
    }

    int getPieceIndex() {
        return pieceIndex;
    }

    int getOffset() {
        return offset;
    }

    int getLength() {
        return length;
    }

    byte[] getBlock() {
        return block;
    }
}
