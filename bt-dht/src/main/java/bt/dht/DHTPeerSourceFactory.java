package bt.dht;

import bt.metainfo.Torrent;
import bt.peer.PeerSource;
import bt.peer.PeerSourceFactory;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DHTPeerSourceFactory implements PeerSourceFactory {

    private DHTService dhtService;
    private ExecutorService executor;

    @Inject
    public DHTPeerSourceFactory(IRuntimeLifecycleBinder lifecycleBinder,
                                DHTService dhtService) {
        this.dhtService = dhtService;
        this.executor = Executors.newFixedThreadPool(10, r -> new Thread(r, "bt.dht.executor"));
        lifecycleBinder.onShutdown(executor::shutdownNow);
    }

    @Override
    public PeerSource getPeerSource(Torrent torrent) {
        return new DHTPeerSource(torrent, dhtService, executor);
    }
}
