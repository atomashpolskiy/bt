package bt.net.extended;

import bt.net.HandshakeHandler;
import bt.net.PeerConnection;
import bt.protocol.Handshake;
import bt.protocol.extended.ExtendedHandshake;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Sets a reserved bit, indicating that
 * BEP-10: Extension Protocol is supported by the local client.
 *
 * @since 1.0
 */
public class ExtendedProtocolHandshakeHandler implements HandshakeHandler {

    private static final int EXTENDED_FLAG_BIT_INDEX = 43;

    private Provider<ExtendedHandshake> extendedHandshakeProvider;

    @Inject
    public ExtendedProtocolHandshakeHandler(Provider<ExtendedHandshake> extendedHandshakeProvider) {
        this.extendedHandshakeProvider = extendedHandshakeProvider;
    }

    @Override
    public void processIncomingHandshake(PeerConnection connection, Handshake peerHandshake) {
        ExtendedHandshake extendedHandshake = extendedHandshakeProvider.get();
        // do not send the extended handshake
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            connection.postMessage(extendedHandshake);
        }
    }

    @Override
    public void processOutgoingHandshake(Handshake handshake) {
        ExtendedHandshake extendedHandshake = extendedHandshakeProvider.get();
        // do not advertise support for the extended protocol
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            handshake.setReservedBit(EXTENDED_FLAG_BIT_INDEX);
        }
    }
}