package bt.net;

import bt.protocol.Handshake;

/**
 * Extension point for additional processing of incoming and outgoing handshakes.
 *
 * @since 1.0
 */
public interface HandshakeHandler {

    /**
     * Process an incoming handshake, received from a remote peer.
     * Implementations are free to send messages via the provided connection
     * and may choose to close it if some of their expectations about the handshake are not met.
     *
     * Attempt to read from the provided connection will trigger an {@link UnsupportedOperationException}.
     *
     * @since 1.1
     */
    void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake);

    /**
     * Make amendments to an outgoing handshake, that will be sent to a remote peer.
     *
     * @since 1.0
     */
    void processOutgoingHandshake(Handshake handshake);
}
