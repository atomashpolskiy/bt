package bt.service;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.net.InetAddress;
import java.util.function.Consumer;

public interface IPeerRegistry {

    Peer getLocalPeer();

    Peer getOrCreatePeer(InetAddress inetAddress, int port);

    void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer);
}
