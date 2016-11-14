package bt.net.extended;

import bt.net.HandshakeHandler;
import bt.net.Peer;
import bt.protocol.Handshake;

public class ExtendedHandshakeHandler implements HandshakeHandler {

    private static final int EXTENDED_FLAG_BIT_INDEX = 43;

    @Override
    public void processIncomingHandshake(Peer peer, Handshake peerHandshake) {
        // do nothing... extended handshake will be processed by the extended protocol handlers
    }

    @Override
    public void amendOutgoingHandshake(Handshake handshake) {
        handshake.setReservedBit(EXTENDED_FLAG_BIT_INDEX);
    }
}