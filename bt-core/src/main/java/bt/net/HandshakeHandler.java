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
     *
     * @since 1.0
     */
    void processIncomingHandshake(Peer peer, Handshake peerHandshake);

    /**
     * Make amendments to an outgoing handshake, that will be sent to a remote peer.
     *
     * @since 1.0
     */
    void processOutgoingHandshake(Handshake handshake);
}
