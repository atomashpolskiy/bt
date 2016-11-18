package bt.net.extended;

import bt.net.HandshakeHandler;
import bt.net.Peer;
import bt.protocol.Handshake;

/**
 * Sets a reserved bit, indicating that
 * BEP-10: Extension Protocol is supported by the local client.
 *
 * @since 1.0
 */
public class ExtendedHandshakeHandler implements HandshakeHandler {

    private static final int EXTENDED_FLAG_BIT_INDEX = 43;

    @Override
    public void processIncomingHandshake(Peer peer, Handshake peerHandshake) {
        // do nothing... extended handshake will be processed by the extended protocol handlers
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        handshake.setReservedBit(EXTENDED_FLAG_BIT_INDEX);
    }
}