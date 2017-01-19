package bt.dht;

import bt.metainfo.Torrent;
import bt.peer.PeerSource;
import bt.peer.PeerSourceFactory;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DHTPeerSourceFactory implements PeerSourceFactory {

    private DHTService dhtService;
    private ExecutorService executor;

    private Map<Torrent, DHTPeerSource> peerSources;

    @Inject
    public DHTPeerSourceFactory(IRuntimeLifecycleBinder lifecycleBinder,
                                DHTService dhtService) {
        this.dhtService = dhtService;
        this.executor = Executors.newCachedThreadPool(r -> new Thread(r, "bt.dht.executor"));
        lifecycleBinder.onShutdown(executor::shutdownNow);

        this.peerSources = new ConcurrentHashMap<>();
    }

    @Override
    public PeerSource getPeerSource(Torrent torrent) {
        DHTPeerSource peerSource = peerSources.get(torrent);
        if (peerSource == null) {
            peerSource = new DHTPeerSource(torrent, dhtService, executor);
            DHTPeerSource existing = peerSources.putIfAbsent(torrent, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }
}
