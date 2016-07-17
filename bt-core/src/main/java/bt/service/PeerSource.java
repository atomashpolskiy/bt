package bt.service;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.util.Collection;

public interface PeerSource {

    Collection<Peer> getPeersForTorrent(Torrent torrent);
}
