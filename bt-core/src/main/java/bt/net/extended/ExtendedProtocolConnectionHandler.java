package bt.net.extended;

import bt.net.ConnectionHandler;
import bt.net.IPeerConnection;
import bt.protocol.extended.ExtendedHandshake;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Sends extended handshake to a remote peer.
 *
 * @since 1.0
 */
public class ExtendedProtocolConnectionHandler implements ConnectionHandler {

    private Provider<ExtendedHandshake> extendedHandshakeProvider;

    @Inject
    public ExtendedProtocolConnectionHandler(Provider<ExtendedHandshake> extendedHandshakeProvider) {
        this.extendedHandshakeProvider = extendedHandshakeProvider;
    }

    @Override
    public boolean handleConnection(IPeerConnection connection) {
        ExtendedHandshake extendedHandshake = extendedHandshakeProvider.get();
        // do not send the extended handshake
        // if local client does not have any extensions turned on
        if (!extendedHandshake.getData().isEmpty()) {
            connection.postMessage(extendedHandshake);
        }
        return true;
    }
}
