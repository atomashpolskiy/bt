package bt.service;

import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Origin;
import bt.net.Peer;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerPeerSourceFactory;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PeerRegistry implements IPeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRegistry.class);

    private static final Origin LOCAL_ORIGIN = new Origin(){};
    private static final Origin ADHOC_ORIGIN = new Origin(){};

    private TrackerPeerSourceFactory trackerPeerSourceFactory;
    private Set<PeerSourceFactory> extraPeerSourceFactories;
    private Map<Torrent, List<Consumer<Peer>>> consumers;
    private final Peer localPeer;

    @Inject
    public PeerRegistry(IRuntimeLifecycleBinder lifecycleBinder, INetworkService networkService,
                        IdService idService, ITrackerService trackerService,
                        Set<PeerSourceFactory> extraPeerSourceFactories) {

        consumers = new ConcurrentHashMap<>();
        localPeer = new InetPeer(networkService.getInetAddress(), networkService.getPort(), LOCAL_ORIGIN,
                idService.getLocalPeerId());

        trackerPeerSourceFactory = new TrackerPeerSourceFactory(trackerService);
        this.extraPeerSourceFactories = extraPeerSourceFactories;

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Peer Registry"));
        lifecycleBinder.onStartup(() -> executor.scheduleAtFixedRate(this::collectAndVisitPeers, 1, 5, TimeUnit.SECONDS));
        lifecycleBinder.onShutdown(this.getClass().getName(), executor::shutdownNow);
    }

    private void collectAndVisitPeers() {

        for (Torrent torrent : consumers.keySet()) {

            List<Consumer<Peer>> peerConsumers = consumers.get(torrent);

            queryPeerSource(trackerPeerSourceFactory.getPeerSource(torrent), peerConsumers);

            // disallow querying peer sources other than tracker for private torrents
            if (!torrent.isPrivate() && !extraPeerSourceFactories.isEmpty()) {
                extraPeerSourceFactories.forEach(factory ->
                        queryPeerSource(factory.getPeerSource(torrent), peerConsumers));
            }
        }
    }

    private void queryPeerSource(PeerSource peerSource, List<Consumer<Peer>> peerConsumers) {
        try {
            if (peerSource.isRefreshable() && peerSource.refresh()) {

                Collection<Peer> discoveredPeers = peerSource.query();
                for (Peer peer : discoveredPeers) {
                    Iterator<Consumer<Peer>> iter = peerConsumers.iterator();
                    while (iter.hasNext()) {
                        Consumer<Peer> consumer = iter.next();
                        try {
                            consumer.accept(peer);
                        } catch (Exception e) {
                            LOGGER.warn("Error in peer consumer", e);
                            iter.remove();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error when querying peer source: " + peerSource, e);
        }
    }

    @Override
    public Peer getLocalPeer() {
        return localPeer;
    }

    @Override
    public Peer getOrCreatePeer(InetAddress inetAddress, int port) {
        return new InetPeer(inetAddress, port, ADHOC_ORIGIN);
    }

    // TODO: remove consumers (e.g. after download is complete)
    @Override
    public void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer) {

        List<Consumer<Peer>> peerConsumers = consumers.get(torrent);
        if (peerConsumers == null) {
            peerConsumers = new ArrayList<>();
            List<Consumer<Peer>> existing = consumers.putIfAbsent(torrent, peerConsumers);
            if (existing != null) {
                peerConsumers = existing;
            }
        }
        peerConsumers.add(consumer);
    }
}
