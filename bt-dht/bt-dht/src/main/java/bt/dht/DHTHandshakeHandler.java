package bt.dht;

import bt.net.HandshakeHandler;
import bt.net.PeerConnection;
import bt.protocol.Handshake;
import bt.protocol.Port;
import com.google.inject.Inject;

import java.io.IOException;

/**
 * @since 1.1
 */
public class DHTHandshakeHandler implements HandshakeHandler {

    private static final int DHT_FLAG_BIT_INDEX = 63;

    private DHTConfig config;

    @Inject
    public DHTHandshakeHandler(DHTConfig config) {
        this.config = config;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        // according to the spec, the client should immediately communicate its' DHT service port
        // upon receiving a handshake indicating DHT support
        if (peerHandshake.isReservedBitSet(DHT_FLAG_BIT_INDEX)) {
            try {
                connection.postMessage(new Port(config.getListeningPort()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to send port to peer: " + connection.getRemotePeer(), e);
            }
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        handshake.setReservedBit(DHT_FLAG_BIT_INDEX);
    }
}
