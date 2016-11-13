package bt.torrent.messaging;

import bt.net.Peer;

public class MessageContext {

    private Peer peer;
    private ConnectionState connectionState;

    MessageContext(Peer peer, ConnectionState connectionState) {
        this.peer = peer;
        this.connectionState = connectionState;
    }

    public Peer getPeer() {
        return peer;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }
}
