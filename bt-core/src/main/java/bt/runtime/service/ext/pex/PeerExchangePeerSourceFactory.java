package bt.runtime.service.ext.pex;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.protocol.Message;
import bt.runtime.protocol.ext.ExtendedHandshake;
import bt.runtime.protocol.ext.pex.PeerExchange;
import bt.service.IShutdownService;
import bt.service.PeerSource;
import bt.service.PeerSourceFactory;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PeerExchangePeerSourceFactory implements PeerSourceFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerExchangePeerSourceFactory.class);

    private static final Duration PEX_INTERVAL = Duration.ofMinutes(1);
    private static final Duration CLEANER_INTERVAL = Duration.ofSeconds(37);
    private static final int MIN_EVENTS_PER_MESSAGE = 10; // TODO: move this to configuration?
    private static final int MAX_EVENTS_PER_MESSAGE = 50;

    private Map<Peer, MessageWorker> workers;
    private Map<Object, PeerExchangePeerSource> peerSources;
    private BlockingQueue<PeerEvent> peerEvents;

    private ReentrantReadWriteLock peerEventsLock;

    @Inject
    public PeerExchangePeerSourceFactory(IShutdownService shutdownService, IPeerConnectionPool connectionPool) {
        connectionPool.addConnectionListener(new Listener());

        workers = new ConcurrentHashMap<>();
        peerSources = new ConcurrentHashMap<>();
        peerEvents = new PriorityBlockingQueue<>();
        peerEventsLock = new ReentrantReadWriteLock();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PEX-Cleaner"));
        executor.scheduleAtFixedRate(new Cleaner(), CLEANER_INTERVAL.toMillis(), CLEANER_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

        shutdownService.addShutdownHook(executor::shutdown);
    }

    @Override
    public PeerSource getPeerSource(Torrent torrent) {
        return getOrCreatePeerSource(torrent.getInfoHash());
    }

    private PeerExchangePeerSource getOrCreatePeerSource(Object torrentId) {
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

    private class Cleaner implements Runnable {

        @Override
        public void run() {
            peerEventsLock.writeLock().lock();
            try {

                long lruEventTime = Long.MAX_VALUE;
                for (MessageWorker worker : workers.values()) {
                    if (worker.isPEXSupported()) {
                        long workerLruEventTime = worker.getLastSentPEXMessageToPeer();
                        if (workerLruEventTime < lruEventTime) {
                            lruEventTime = workerLruEventTime;
                        }
                    }
                }

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
                peerEventsLock.writeLock().unlock();
            }
        }
    }

    private class Listener implements PeerActivityListener {

        @Override
        public void onPeerConnected(Object torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                                    Consumer<Supplier<Message>> messageSuppliers) {

            peerEvents.add(PeerEvent.addedPeer(peer));

            MessageWorker worker = workers.get(peer);
            if (worker == null) {
                worker = new MessageWorker(getOrCreatePeerSource(torrentId));
                MessageWorker existing = workers.putIfAbsent(peer, worker);
                if (existing == null) {
                    messageConsumers.accept(worker);
                    messageSuppliers.accept(worker);
                }
            }
        }

        @Override
        public void onPeerDisconnected(Peer peer) {

            peerEvents.add(PeerEvent.droppedPeer(peer));
            workers.remove(peer);
        }
    }

    private class MessageWorker implements Consumer<Message>, Supplier<Message> {

        private PeerExchangePeerSource peerSource;

        private volatile boolean peerSupportsPEX;
        private volatile long lastSentPEXMessageToPeer;

        MessageWorker(PeerExchangePeerSource peerSource) {
            this.peerSource = peerSource;
            lastSentPEXMessageToPeer = 0;
        }

        boolean isPEXSupported() {
            return peerSupportsPEX;
        }

        long getLastSentPEXMessageToPeer() {
            return lastSentPEXMessageToPeer;
        }

        @Override
        public void accept(Message message) {

            if (ExtendedHandshake.class.equals(message.getClass())) {
                ExtendedHandshake handshake = (ExtendedHandshake) message;
                if (handshake.getSupportedMessageTypes().contains("ut_pex")) {
                    // TODO: peer may eventually turn off the PEX extension
                    // moreover the extended handshake message type map is additive,
                    // so we can't learn about the peer turning off extensions solely from the message
                    peerSupportsPEX = true;
                }

            } else if (PeerExchange.class.equals(message.getClass())) {
                PeerExchange peerExchange = (PeerExchange) message;
                peerSource.addMessage(peerExchange);
            }
        }

        @Override
        public Message get() {

            long currentTime = System.currentTimeMillis();

            if (peerSupportsPEX && (currentTime - lastSentPEXMessageToPeer) - PEX_INTERVAL.toMillis() >= 0) {

                List<PeerEvent> events = new ArrayList<>();

                peerEventsLock.readLock().lock();
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
                    peerEventsLock.readLock().unlock();
                }

                if (events.size() >= MIN_EVENTS_PER_MESSAGE) {

                    lastSentPEXMessageToPeer = currentTime;

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
                    return messageBuilder.build();
                }
            }
            return null;
        }
    }
}
