package bt.net;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IPeerConnectionPool {

    void addConnectionListener(Consumer<IPeerConnection> listener);

    void removeConnectionListener(Consumer<IPeerConnection> listener);

    IPeerConnection getConnection(Peer peer);

    CompletableFuture<IPeerConnection> requestConnection(Peer peer, HandshakeHandler handshakeHandler);
}
