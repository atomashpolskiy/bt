package bt.service;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.net.InetAddress;
import java.util.Collection;

public interface IPeerRegistry {

    Peer getLocalPeer();

    Peer getOrCreatePeer(InetAddress inetAddress, int port);

    Collection<Peer> getPeersForTorrent(Torrent torrent);
}
