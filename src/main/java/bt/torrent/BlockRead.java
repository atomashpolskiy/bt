package bt.torrent;

import bt.net.Peer;
import bt.protocol.Request;

class BlockRead {

    private Peer peer;
    private Request request;
    private byte[] block;

    BlockRead(Peer peer, Request request) {
        this.peer = peer;
        this.request = request;
    }

    void setBlock(byte[] block) {
        this.block = block;
    }

    Peer getPeer() {
        return peer;
    }

    Request getRequest() {
        return request;
    }

    byte[] getBlock() {
        return block;
    }
}
