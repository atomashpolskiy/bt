package bt.peer;

import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    private ITrackerService trackerService;
    private TrackerPeerSourceFactory trackerPeerSourceFactory;
    private Set<PeerSourceFactory> extraPeerSourceFactories;

    private ConcurrentMap<Torrent, List<Consumer<Peer>>> peerConsumers;

    public PeerRegistry(IRuntimeLifecycleBinder lifecycleBinder,
                        IdentityService idService,
                        ITrackerService trackerService,
                        Set<PeerSourceFactory> extraPeerSourceFactories,
                        InetAddress localPeerAddress,
                        int localPeerPort,
                        Duration peerDiscoveryInterval,
                        Duration trackerQueryInterval) {

        this.peerConsumers = new ConcurrentHashMap<>();
        this.localPeer = new InetPeer(localPeerAddress, localPeerPort, idService.getLocalPeerId());

        this.trackerService = trackerService;
        this.trackerPeerSourceFactory = new TrackerPeerSourceFactory(trackerService, lifecycleBinder, trackerQueryInterval);
        this.extraPeerSourceFactories = extraPeerSourceFactories;

        createExecutor(lifecycleBinder, peerDiscoveryInterval);
    }

    private void createExecutor(IRuntimeLifecycleBinder lifecycleBinder, Duration peerDiscoveryInterval) {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peer.peer-collector"));
        lifecycleBinder.onStartup("Schedule periodic peer lookup", () -> executor.scheduleAtFixedRate(
                this::collectAndVisitPeers, 1, peerDiscoveryInterval.getSeconds(), TimeUnit.SECONDS));
        lifecycleBinder.onShutdown("Shutdown peer lookup scheduler", executor::shutdownNow);
    }

    private void collectAndVisitPeers() {
        peerConsumers.forEach((torrent, consumers) -> {
            queryTracker(torrent, consumers);

            // disallow querying peer sources other than the tracker for private torrents
            if (!torrent.isPrivate() && !extraPeerSourceFactories.isEmpty()) {
                extraPeerSourceFactories.forEach(factory ->
                        queryPeerSource(factory.getPeerSource(torrent), consumers));
            }
        });
    }

    private void queryTracker(Torrent torrent, List<Consumer<Peer>> consumers) {
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
        if (announceKey.isPresent() && mightCreateTracker(announceKey.get())) {
            queryPeerSource(trackerPeerSourceFactory.getPeerSource(torrent), consumers);
        }
    }

    private boolean mightCreateTracker(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            // TODO: need some more sophisticated solution because some of the trackers might be supported
            for (List<String> tier : announceKey.getTrackerUrls()) {
                for (String trackerUrl : tier) {
                    if (!trackerService.isSupportedProtocol(trackerUrl)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return trackerService.isSupportedProtocol(announceKey.getTrackerUrl());
        }
    }

    private void queryPeerSource(PeerSource peerSource, List<Consumer<Peer>> peerConsumers) {
        try {
            if (peerSource.update()) {
                Collection<Peer> discoveredPeers = peerSource.getPeers();
                for (Peer peer : discoveredPeers) {
                    for (Consumer<Peer> consumer : peerConsumers) {
                        try {
                            consumer.accept(peer);
                        } catch (Exception e) {
                            LOGGER.error("Error in peer consumer", e);
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
