package bt.dht;

import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.peer.ScheduledPeerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @since 1.1
 */
public class DHTPeerSource extends ScheduledPeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DHTPeerSource.class);

    private static final int MAX_PEERS_PER_COLLECTION = 50;

    private final Torrent torrent;
    private final DHTService dhtService;

    DHTPeerSource(Torrent torrent, DHTService dhtService, ExecutorService executor) {
        super(executor);
        this.torrent = torrent;
        this.dhtService = dhtService;
    }

    @Override
    protected void collectPeers(Consumer<Peer> peerConsumer) {
        Stream<Peer> peerStream = dhtService.getPeers(torrent).limit(MAX_PEERS_PER_COLLECTION);
        peerStream.forEach(peer -> {
            peerConsumer.accept(peer);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Collected new peer (torrent ID: %s, peer: %s)", torrent.getTorrentId(), peer));
            }
        });
        LOGGER.info("Peer collection finished for torrent ID: " + torrent.getTorrentId());
    }
}
