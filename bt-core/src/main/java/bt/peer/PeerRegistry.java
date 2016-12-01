package bt.peer;

import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.service.INetworkService;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.tracker.ITrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerRegistry implements IPeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRegistry.class);

    private final Peer localPeer;

    private TrackerPeerSourceFactory trackerPeerSourceFactory;
    private Set<PeerSourceFactory> extraPeerSourceFactories;

    private ConcurrentMap<Torrent, List<Consumer<Peer>>> peerConsumers;

    public PeerRegistry(IRuntimeLifecycleBinder lifecycleBinder,
                        INetworkService networkService,
                        IdentityService idService,
                        ITrackerService trackerService,
                        Set<PeerSourceFactory> extraPeerSourceFactories,
                        Duration peerDiscoveryInterval,
                        Duration trackerQueryInterval) {

        this.peerConsumers = new ConcurrentHashMap<>();
        this.localPeer = new InetPeer(networkService.getInetAddress(), networkService.getPort(), idService.getLocalPeerId());

        this.trackerPeerSourceFactory = new TrackerPeerSourceFactory(trackerService, trackerQueryInterval);
        this.extraPeerSourceFactories = extraPeerSourceFactories;

        createExecutor(lifecycleBinder, peerDiscoveryInterval);
    }

    private void createExecutor(IRuntimeLifecycleBinder lifecycleBinder, Duration peerDiscoveryInterval) {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peer.peer-collector"));
        lifecycleBinder.onStartup(() -> executor.scheduleAtFixedRate(
                this::collectAndVisitPeers, 1, peerDiscoveryInterval.getSeconds(), TimeUnit.SECONDS));
        lifecycleBinder.onShutdown(this.getClass().getName(), executor::shutdownNow);
    }

    private void collectAndVisitPeers() {
        peerConsumers.forEach((torrent, consumers) -> {
            queryPeerSource(trackerPeerSourceFactory.getPeerSource(torrent), consumers);

            // disallow querying peer sources other than tracker for private torrents
            if (!torrent.isPrivate() && !extraPeerSourceFactories.isEmpty()) {
                extraPeerSourceFactories.forEach(factory ->
                        queryPeerSource(factory.getPeerSource(torrent), consumers));
            }
        });
    }

    private void queryPeerSource(PeerSource peerSource, List<Consumer<Peer>> peerConsumers) {
        try {
            if (peerSource.update()) {
                Collection<Peer> discoveredPeers = peerSource.getPeers();
                for (Peer peer : discoveredPeers) {
                    Iterator<Consumer<Peer>> iter = peerConsumers.iterator();
                    while (iter.hasNext()) {
                        Consumer<Peer> consumer = iter.next();
                        try {
                            consumer.accept(peer);
                        } catch (Exception e) {
                            LOGGER.error("Error in peer consumer", e);
                            iter.remove();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error when querying peer source: " + peerSource, e);
        }
    }

    @Override
    public Peer getLocalPeer() {
        return localPeer;
    }

    @Override
    public void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer) {
        List<Consumer<Peer>> consumers = peerConsumers.get(torrent);
        if (consumers == null) {
            consumers = new ArrayList<>();
            List<Consumer<Peer>> existing = peerConsumers.putIfAbsent(torrent, consumers);
            if (existing != null) {
                consumers = existing;
            }
        }
        consumers.add(consumer);
    }

    // TODO: someone should call this after torrent is stopped/completed
    @Override
    public void removePeerConsumers(Torrent torrent) {
        peerConsumers.remove(torrent);
    }
}
