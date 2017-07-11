package bt.peer;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.net.PeerId;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.IdentityService;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerRegistry implements IPeerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRegistry.class);

    private final Peer localPeer;
    private final PeerCache cache;

    private TorrentRegistry torrentRegistry;
    private ITrackerService trackerService;
    private TrackerPeerSourceFactory trackerPeerSourceFactory;
    private Set<PeerSourceFactory> extraPeerSourceFactories;

    private ConcurrentMap<TorrentId, List<Consumer<Peer>>> peerConsumers;

    public PeerRegistry(IRuntimeLifecycleBinder lifecycleBinder,
                        IdentityService idService,
                        TorrentRegistry torrentRegistry,
                        ITrackerService trackerService,
                        Set<PeerSourceFactory> extraPeerSourceFactories,
                        InetAddress localPeerAddress,
                        int localPeerPort,
                        Duration peerDiscoveryInterval,
                        Duration trackerQueryInterval) {

        this.peerConsumers = new ConcurrentHashMap<>();
        this.localPeer = new InetPeer(localPeerAddress, localPeerPort, idService.getLocalPeerId());
        this.cache = new PeerCache();

        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
        this.trackerPeerSourceFactory = new TrackerPeerSourceFactory(trackerService, torrentRegistry, lifecycleBinder, trackerQueryInterval);
        this.extraPeerSourceFactories = extraPeerSourceFactories;

        createExecutor(lifecycleBinder, peerDiscoveryInterval);
    }

    private void createExecutor(IRuntimeLifecycleBinder lifecycleBinder, Duration peerDiscoveryInterval) {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peer.peer-collector"));
        lifecycleBinder.onStartup("Schedule periodic peer lookup", () -> executor.scheduleAtFixedRate(
                this::collectAndVisitPeers, 1, peerDiscoveryInterval.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown("Shutdown peer lookup scheduler", executor::shutdownNow);
    }

    private void collectAndVisitPeers() {
        peerConsumers.forEach((torrentId, consumers) -> {
            Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
            if (descriptor.isPresent() && descriptor.get().isActive()) {
                Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);
                if (torrentOptional.isPresent()) {
                    Torrent torrent = torrentOptional.get();
                    queryTracker(torrent, consumers);
                }

                // disallow querying peer sources other than the tracker for private torrents
                if ((!torrentOptional.isPresent() || !torrentOptional.get().isPrivate()) && !extraPeerSourceFactories.isEmpty()) {
                    extraPeerSourceFactories.forEach(factory ->
                            queryPeerSource(factory.getPeerSource(torrentId), consumers));
                }
            }
        });
    }

    private void queryTracker(Torrent torrent, List<Consumer<Peer>> consumers) {
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
        if (announceKey.isPresent() && mightCreateTracker(announceKey.get())) {
            queryPeerSource(trackerPeerSourceFactory.getPeerSource(torrent.getTorrentId()), consumers);
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
                    if (isLocal(peer)) {
                        continue;
                    }
                    cache.registerPeer(peer);
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

    private boolean isLocal(Peer peer) {
        return peer.getInetAddress().isAnyLocalAddress() && localPeer.getPort() == peer.getPort();
    }

    @Override
    public Peer getLocalPeer() {
        return localPeer;
    }

    @Override
    public Peer getPeerForAddress(InetSocketAddress address) {
        return cache.getPeerForAddress(address);
    }

    @Override
    public void addPeerConsumer(Torrent torrent, Consumer<Peer> consumer) {
        addPeerConsumer(torrent.getTorrentId(), consumer);
    }

    @Override
    public void addPeerConsumer(TorrentId torrentId, Consumer<Peer> consumer) {
        List<Consumer<Peer>> consumers = peerConsumers.get(torrentId);
        if (consumers == null) {
            consumers = new ArrayList<>();
            List<Consumer<Peer>> existing = peerConsumers.putIfAbsent(torrentId, consumers);
            if (existing != null) {
                consumers = existing;
            }
        }
        consumers.add(consumer);
    }

    // TODO: someone should call this after torrent is stopped/completed
    @Override
    public void removePeerConsumers(Torrent torrent) {
        removePeerConsumers(torrent.getTorrentId());
    }

    @Override
    public void removePeerConsumers(TorrentId torrentId) {
        peerConsumers.remove(torrentId);
    }

    private static class PeerCache {
        // all known peers (lookup by inet address)
        private final ConcurrentMap<InetSocketAddress, UpdatablePeer> knownPeers;
        private final ReentrantLock peerLock;

        PeerCache() {
            this.knownPeers = new ConcurrentHashMap<>();
            this.peerLock = new ReentrantLock();
        }

        // need to do this atomically:
        // - concurrent call to getPeerForAddress(InetSocketAddress)
        //   might coincide with querying peer sources (overwriting options, etc)
        private UpdatablePeer registerPeer(Peer peer) {
            peerLock.lock();
            try {
                UpdatablePeer newPeer = new UpdatablePeer(peer);
                UpdatablePeer existing = knownPeers.putIfAbsent(peer.getInetSocketAddress(), newPeer);
                if (existing != null) {
                    existing.setOptions(peer.getOptions());
                }
                return (existing == null) ? newPeer : existing;
            } finally {
                peerLock.unlock();
            }
        }

        public Peer getPeerForAddress(InetSocketAddress address) {
            Peer existing = knownPeers.get(address);
            if (existing == null) {
                peerLock.lock();
                try {
                    existing = knownPeers.get(address);
                    if (existing == null) {
                        existing = registerPeer(new InetPeer(address));
                    }
                } finally {
                    peerLock.unlock();
                }
            }
            return existing;
        }
    }

    private static class UpdatablePeer implements Peer {
        private final Peer delegate;
        private volatile PeerOptions options;

        UpdatablePeer(Peer delegate) {
            super();
            this.delegate = delegate;
            this.options = delegate.getOptions();
        }

        @Override
        public InetSocketAddress getInetSocketAddress() {
            return delegate.getInetSocketAddress();
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public Optional<PeerId> getPeerId() {
            return delegate.getPeerId();
        }

        @Override
        public PeerOptions getOptions() {
            return options;
        }

        void setOptions(PeerOptions options) {
            this.options = options;
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return delegate.equals(object);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
