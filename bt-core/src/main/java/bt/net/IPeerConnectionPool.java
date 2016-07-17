package bt.net;

import java.util.concurrent.CompletableFuture;

public interface IPeerConnectionPool {

    void addConnectionListener(PeerActivityListener listener);

    void removeConnectionListener(PeerActivityListener listener);

    IPeerConnection getConnection(Peer peer);

    CompletableFuture<IPeerConnection> requestConnection(Peer peer, ConnectionHandler connectionHandler);
}
