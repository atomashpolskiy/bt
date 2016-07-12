package bt.net;

import bt.protocol.ext.ExtendedHandshake;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class ExtendedHandshakeHandler implements HandshakeHandler {

    private Provider<ExtendedHandshake> extendedHandshakeProvider;

    @Inject
    public ExtendedHandshakeHandler(Provider<ExtendedHandshake> extendedHandshakeProvider) {
        this.extendedHandshakeProvider = extendedHandshakeProvider;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        connection.postMessage(extendedHandshakeProvider.get());
        return true;
    }
}
