package bt.net.extended;

import bt.net.ConnectionHandler;
import bt.net.PeerConnection;
import bt.protocol.extended.ExtendedHandshake;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class ExtendedConnectionHandler implements ConnectionHandler {

    private Provider<ExtendedHandshake> extendedHandshakeProvider;

    @Inject
    public ExtendedConnectionHandler(Provider<ExtendedHandshake> extendedHandshakeProvider) {
        this.extendedHandshakeProvider = extendedHandshakeProvider;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        connection.postMessage(extendedHandshakeProvider.get());
        return true;
    }
}
