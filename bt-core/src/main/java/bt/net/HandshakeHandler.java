package bt.net;

import bt.protocol.Handshake;

public interface HandshakeHandler {

    void processIncomingHandshake(Peer peer, Handshake peerHandshake);

    void amendOutgoingHandshake(Handshake handshake);
}
