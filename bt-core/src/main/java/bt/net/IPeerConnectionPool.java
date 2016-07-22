package bt.net;

import java.util.concurrent.CompletableFuture;

public interface IPeerConnectionPool {

    void addConnectionListener(PeerActivityListener listener);

    IPeerConnection getConnection(Peer peer);

    CompletableFuture<IPeerConnection> requestConnection(Peer peer, ConnectionHandler connectionHandler);
}
