package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.util.stream.Stream;

public interface DHTService {

    Stream<Peer> getPeers(Torrent torrent);
}
