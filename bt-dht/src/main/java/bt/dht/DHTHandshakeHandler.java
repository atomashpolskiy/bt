package bt.dht;

import bt.net.HandshakeHandler;
import bt.net.Peer;
import bt.protocol.Handshake;
import bt.torrent.messaging.DHTMessagingAgent;
import com.google.inject.Inject;

/**
 * @since 1.1
 */
public class DHTHandshakeHandler implements HandshakeHandler {

    private static final int DHT_FLAG_BIT_INDEX = 63;

    private DHTMessagingAgent messagingAgent;

    @Inject
    public DHTHandshakeHandler(DHTMessagingAgent messagingAgent) {
        this.messagingAgent = messagingAgent;
    }

    @Override
    public void processIncomingHandshake(Peer peer, Handshake peerHandshake) {
        // according to the spec, the client should immediately communicate its' DHT service port
        // upon receiving a handshake indicating DHT support
        if (peerHandshake.isReservedBitSet(DHT_FLAG_BIT_INDEX)) {
            messagingAgent.shouldCommunicatePortTo(peer);
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        handshake.setReservedBit(DHT_FLAG_BIT_INDEX);
    }
}
