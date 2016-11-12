package bt.net;

import java.util.concurrent.CompletableFuture;

public interface IPeerConnectionPool {

    void addConnectionListener(PeerActivityListener listener);

    IPeerConnection getConnection(Peer peer);

    // TODO: remove ConnectionHandler param (?)
    CompletableFuture<IPeerConnection> requestConnection(Peer peer, ConnectionHandler connectionHandler);
}
