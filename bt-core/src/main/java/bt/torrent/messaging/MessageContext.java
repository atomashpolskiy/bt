package bt.torrent.messaging;

import bt.net.Peer;

/**
 * Provides basic information about the context of a message (both inbound and outbound).
 *
 * @since 1.0
 */
public class MessageContext {

    private Peer peer;
    private ConnectionState connectionState;

    MessageContext(Peer peer, ConnectionState connectionState) {
        this.peer = peer;
        this.connectionState = connectionState;
    }

    /**
     * @return Remote peer
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }
}
