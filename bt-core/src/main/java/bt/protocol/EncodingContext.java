package bt.protocol;

import bt.net.Peer;

public class EncodingContext {

    private final Peer peer;

    public EncodingContext(Peer peer) {
        this.peer = peer;
    }

    public Peer getPeer() {
        return peer;
    }
}
