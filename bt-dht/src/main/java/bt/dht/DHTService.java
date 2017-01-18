package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.util.Collection;

interface DHTService {

    void start();

    void shutdown();

    Collection<Peer> getPeers(Torrent torrent);
}
