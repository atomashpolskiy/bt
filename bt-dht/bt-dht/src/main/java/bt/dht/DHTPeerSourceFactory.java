package bt.dht;

import bt.metainfo.TorrentId;
import bt.peer.PeerSource;
import bt.peer.PeerSourceFactory;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factory of DHT peer sources
 *
 * @since 1.1
 */
public class DHTPeerSourceFactory implements PeerSourceFactory {

    private DHTService dhtService;
    private ExecutorService executor;

    private Map<TorrentId, DHTPeerSource> peerSources;

    @Inject
    public DHTPeerSourceFactory(IRuntimeLifecycleBinder lifecycleBinder,
                                DHTService dhtService) {
        this.dhtService = dhtService;
        this.executor = Executors.newCachedThreadPool(r -> new Thread(r, "bt.dht.executor"));
        lifecycleBinder.onShutdown("Shutdown DHT peer sources", executor::shutdownNow);

        this.peerSources = new ConcurrentHashMap<>();
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        DHTPeerSource peerSource = peerSources.get(torrentId);
        if (peerSource == null) {
            peerSource = new DHTPeerSource(torrentId, dhtService, executor);
            DHTPeerSource existing = peerSources.putIfAbsent(torrentId, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
