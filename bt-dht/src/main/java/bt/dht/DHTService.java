package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.util.stream.Stream;

interface DHTService {

    void start();

    void shutdown();

    Stream<Peer> getPeers(Torrent torrent);
}
