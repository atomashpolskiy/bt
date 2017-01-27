package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;

import java.util.stream.Stream;

/**
 * Provides an interface to DHT services
 *
 * @since 1.1
 */
public interface DHTService {

    /**
     * Creates a peer lookup for a given torrent.
     * Blocks the caller thread if the DHT services are unavailable at the moment of calling.
     *
     * @return Stream of peers.
     *         Retrieval of the next element might block.
     *         The stream ends when the current lookup is exhausted.
     * @since 1.1
     */
    Stream<Peer> getPeers(Torrent torrent);

    /**
     * Add a DHT node.
     *
     * @param node DHT node
     * @since 1.1
     */
    void addNode(Peer node);
}
