package bt.peerexchange.service;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.peerexchange.protocol.PeerExchange;
import bt.protocol.Message;
import bt.protocol.extended.ExtendedHandshake;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.PeerSource;
import bt.service.PeerSourceFactory;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.MessageContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class PeerExchangePeerSourceFactory implements PeerSourceFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerExchangePeerSourceFactory.class);

    private static final Duration CLEANER_INTERVAL = Duration.ofSeconds(37);
    private static final Duration PEX_INTERVAL = Duration.ofMinutes(1);
    private static final int MIN_EVENTS_PER_MESSAGE = 10; // TODO: move this to configuration?
    private static final int MAX_EVENTS_PER_MESSAGE = 50;

    private Map<TorrentId, PeerExchangePeerSource> peerSources;

    private BlockingQueue<PeerEvent> peerEvents;
    private ReentrantReadWriteLock rwLock;

    private Set<Peer> peers;
    private Map<Peer, Long> lastSentPEXMessage;

    @Inject
    public PeerExchangePeerSourceFactory(Provider<IPeerConnectionPool> connectionPoolProvider,
                                  IRuntimeLifecycleBinder lifecycleBinder) {
        this.peerSources = new ConcurrentHashMap<>();
        this.peerEvents = new PriorityBlockingQueue<>();
        this.rwLock = new ReentrantReadWriteLock();
        this.peers = ConcurrentHashMap.newKeySet();
        this.lastSentPEXMessage = new ConcurrentHashMap<>();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PEX-Cleaner"));
        lifecycleBinder.onStartup(() -> connectionPoolProvider.get()
                .addConnectionListener(createPeerActivityListener()));
        lifecycleBinder.onStartup(() -> executor.scheduleAtFixedRate(
                new Cleaner(), CLEANER_INTERVAL.toMillis(), CLEANER_INTERVAL.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown(this.getClass().getName(), executor::shutdownNow);
    }

    private PeerActivityListener createPeerActivityListener() {
        return new PeerActivityListener() {
            @Override
            public void onPeerDiscovered(Peer peer) {
                // ignore
            }

            @Override
            public void onPeerConnected(TorrentId torrentId, Peer peer) {
                peerEvents.add(PeerEvent.added(peer));
            }

            @Override
            public void onPeerDisconnected(Peer peer) {
                peerEvents.add(PeerEvent.dropped(peer));
                peers.remove(peer);
                lastSentPEXMessage.remove(peer);
            }
        };
    }

    @Override
    public PeerSource getPeerSource(Torrent torrent) {
        return getOrCreatePeerSource(torrent.getTorrentId());
    }

    private PeerExchangePeerSource getOrCreatePeerSource(TorrentId torrentId) {
        PeerExchangePeerSource peerSource = peerSources.get(torrentId);
        if (peerSource == null) {
            peerSource = new PeerExchangePeerSource();
            PeerExchangePeerSource existing = peerSources.putIfAbsent(torrentId, peerSource);
            if (existing != null) {
                peerSource = existing;
            }
        }
        return peerSource;
    }

    @Consumes
    public void consume(ExtendedHandshake handshake, MessageContext messageContext) {
        if (handshake.getSupportedMessageTypes().contains("ut_pex")) {
            // TODO: peer may eventually turn off the PEX extension
            // moreover the extended handshake message type map is additive,
            // so we can't learn about the peer turning off extensions solely from the message
            peers.add(messageContext.getPeer());
        }
    }

    @Consumes
    public void consume(PeerExchange message, MessageContext messageContext) {
        messageContext.getTorrentId().ifPresent(torrentId ->
                getOrCreatePeerSource(torrentId).addMessage(message));
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext messageContext) {
        Peer peer = messageContext.getPeer();
        long currentTime = System.currentTimeMillis();
        long lastSentPEXMessageToPeer = lastSentPEXMessage.getOrDefault(peer, 0L);

        if (peers.contains(peer) && (currentTime - lastSentPEXMessageToPeer) - PEX_INTERVAL.toMillis() >= 0) {
            List<PeerEvent> events = new ArrayList<>();

            rwLock.readLock().lock();
            try {
                for (PeerEvent event : peerEvents) {
                    if (event.getInstant() - lastSentPEXMessageToPeer >= 0) {
                        events.add(event);
                    }
                    if (events.size() >= MAX_EVENTS_PER_MESSAGE) {
                        break;
                    }
                }
            } finally {
                rwLock.readLock().unlock();
            }

            if (events.size() >= MIN_EVENTS_PER_MESSAGE) {
                lastSentPEXMessage.put(peer, currentTime);
                PeerExchange.Builder messageBuilder = PeerExchange.builder();
                events.forEach(event -> {
                    switch (event.getType()) {
                        case ADDED: {
                            messageBuilder.added(event.getPeer());
                            break;
                        }
                        case DROPPED: {
                            messageBuilder.dropped(event.getPeer());
                            break;
                        }
                        default: {
                            throw new BtException("Unknown event type: " + event.getType().name());
                        }
                    }
                });
                messageConsumer.accept(messageBuilder.build());
            }
        }
    }

    private class Cleaner implements Runnable {

        @Override
        public void run() {
            rwLock.writeLock().lock();
            try {
                long lruEventTime = lastSentPEXMessage.values().stream()
                        .reduce(Long.MAX_VALUE, (a, b) -> (a < b) ? a : b);;

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Prior to cleaning events. LRU event time: {}, peer events: {}", lruEventTime, peerEvents);
                }

                PeerEvent event;
                while ((event = peerEvents.peek()) != null && event.getInstant() <= lruEventTime) {
                    peerEvents.poll();
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("After cleaning events. Peer events: {}", peerEvents);
                }

            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }
}
