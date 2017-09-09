package bt.net;

import bt.metainfo.TorrentId;
import bt.module.PeerConnectionSelector;
import bt.protocol.Message;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default single-threaded message dispatcher implementation.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class MessageDispatcher implements IMessageDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatcher.class);

    private final Map<Peer, Collection<Consumer<Message>>> consumers;
    private final Map<Peer, Collection<Supplier<Message>>> suppliers;

    private TorrentRegistry torrentRegistry;

    @Inject
    public MessageDispatcher(IRuntimeLifecycleBinder lifecycleBinder,
                             IPeerConnectionPool pool,
                             TorrentRegistry torrentRegistry,
                             @PeerConnectionSelector SharedSelector selector,
                             Config config) {

        this.consumers = new ConcurrentHashMap<>();
        this.suppliers = new ConcurrentHashMap<>();
        this.torrentRegistry = torrentRegistry;

        Queue<PeerMessage> messages = new LinkedBlockingQueue<>(); // shared message queue
        initializeMessageLoop(lifecycleBinder, pool, messages, config);
        initializeMessageReceiver(selector, lifecycleBinder, messages);
    }

    private void initializeMessageLoop(IRuntimeLifecycleBinder lifecycleBinder,
                                       IPeerConnectionPool pool,
                                       Queue<PeerMessage> messages,
                                       Config config) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.message-dispatcher"));
        LoopControl loopControl = new LoopControl(config.getMaxMessageProcessingInterval().toMillis());
        MessageDispatchingLoop loop = new MessageDispatchingLoop(pool, loopControl, messages::poll);
        lifecycleBinder.onStartup("Initialize message dispatcher", () -> executor.execute(loop));
        lifecycleBinder.onShutdown("Shutdown message dispatcher", () -> {
            try {
                loop.shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private void initializeMessageReceiver(SharedSelector selector,
                                           IRuntimeLifecycleBinder lifecycleBinder,
                                           Queue<PeerMessage> messages) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.message-receiver"));
        BiConsumer<Peer, Message> messageSink = (peer, message) ->
                messages.add(new PeerMessage(Objects.requireNonNull(peer), Objects.requireNonNull(message)));
        MessageReceivingLoop loop = new MessageReceivingLoop(selector, messageSink);
        lifecycleBinder.onStartup("Initialize message receiver", () -> executor.execute(loop));
        lifecycleBinder.onShutdown("Shutdown message receiver", () -> {
            try {
                loop.shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private class PeerMessage {
        private final Peer peer;
        private final Message message;

        public PeerMessage(Peer peer, Message message) {
            this.peer = peer;
            this.message = message;
        }

        public Peer getPeer() {
            return peer;
        }

        public Message getMessage() {
            return message;
        }
    }

    private class MessageReceivingLoop implements Runnable {

        private final SharedSelector selector;
        private final BiConsumer<Peer, Message> messageSink;
        private volatile boolean shutdown;

        MessageReceivingLoop(SharedSelector selector, BiConsumer<Peer, Message> messageSink) {
            this.selector = selector;
            this.messageSink = messageSink;
        }

        @Override
        public void run() {
            while (!shutdown) {
                if (!selector.isOpen()) {
                    LOGGER.info("Selector is closed, stopping...");
                    break;
                }

                try {
                    // wakeup periodically to check if there are unprocessed keys left
                    while (selector.select(1000) == 0 && selector.selectedKeys().isEmpty()) {
                        Thread.yield();
                    }

                    Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        try {
                            // do not remove the key if it hasn't been processed,
                            // we'll try again in the next loop iteration
                            if (processKey(selectedKeys.next())) {
                                selectedKeys.remove();
                            }
                        } catch (ClosedSelectorException e) {
                            // selector has been closed, there's no point to continue processing
                            throw e;
                        } catch (Exception e) {
                            LOGGER.error("Failed to process key", e);
                            selectedKeys.remove();
                        }
                    }
                } catch (ClosedSelectorException e) {
                    LOGGER.info("Selector has been closed, will stop receiving messages...");
                    return;
                } catch (IOException e) {
                    throw new RuntimeException("Unexpected I/O exception when selecting peer connections", e);
                }
            }
        }

        /**
         * @return true, if the key has been processed and can be removed
         */
        private boolean processKey(SelectionKey key) {
            Object obj = key.attachment();
            if (obj == null || !(obj instanceof PeerConnection)) {
                LOGGER.warn("Unexpected attachment in selection key: {}. Skipping..", obj);
                return false;
            }

            PeerConnection connection = (PeerConnection) obj;
            Peer peer = connection.getRemotePeer();
            if (!key.isValid() || !key.isReadable()) {
                LOGGER.warn("Selected connection for peer {}, but the key is cancelled or read op is not indicated. Skipping..", peer);
                return false;
            }

            if (connection.isClosed()) {
                LOGGER.warn("Selected connection for peer {}, but the connection has already been closed. Skipping..", peer);
                return false;
            }

            TorrentId torrentId = connection.getTorrentId();
            if (torrentId == null) {
                LOGGER.warn("Selected connection for peer {}, but the connection does not indicate a torrent ID. Skipping..", peer);
                return false;
            } else if (!isSupportedAndActive(torrentId)) {
                LOGGER.warn("Selected connection for peer {}, but the torrent ID is unknown or inactive: {}. Skipping..", peer, torrentId);
                return false;
            }

            while (true) {
                Message message = null;
                try {
                    message = connection.readMessageNow();
                } catch (Exception e) {
                    LOGGER.error("Error when reading message from peer: " + peer, e);
                    connection.closeQuietly();
                    key.cancel();
                }
                if (message == null) {
                    break;
                } else {
                    messageSink.accept(peer, message);
                }
            }
            return true;
        }

        public void shutdown() {
            shutdown = true;
        }
    }

    private class MessageDispatchingLoop implements Runnable {

        private final IPeerConnectionPool pool;
        private final LoopControl loopControl;
        private final Supplier<PeerMessage> messageSource;

        private volatile boolean shutdown;

        MessageDispatchingLoop(IPeerConnectionPool pool, LoopControl loopControl, Supplier<PeerMessage> messageSource) {
            this.pool = pool;
            this.loopControl = loopControl;
            this.messageSource = messageSource;
        }

        @Override
        public void run() {
            while (!shutdown) {
                // TODO: restrict max amount of incoming messages per iteration
                PeerMessage envelope;
                while ((envelope = messageSource.get()) != null) {
                    loopControl.incrementProcessed();

                    Peer peer = envelope.getPeer();
                    Message message = envelope.getMessage();

                    Collection<Consumer<Message>> peerConsumers = consumers.get(peer);
                    if (peerConsumers != null && peerConsumers.size() > 0) {
                        for (Consumer<Message> consumer : peerConsumers) {
                            try {
                                consumer.accept(message);
                            } catch (Exception e) {
                                LOGGER.error("Unexpected exception when processing message " + message + " for peer " + peer, e);
                            }
                        }
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Discarding message {} for peer {}, because no consumers were registered", message, peer);
                        }
                    }
                }

                if (!suppliers.isEmpty()) {
                    for (Map.Entry<Peer, Collection<Supplier<Message>>> entry : suppliers.entrySet()) {
                        Peer peer = entry.getKey();
                        Collection<Supplier<Message>> suppliers = entry.getValue();

                        PeerConnection connection = pool.getConnection(peer);
                        if (connection != null && !connection.isClosed()) {
                            if (isSupportedAndActive(connection.getTorrentId())) {
                                for (Supplier<Message> messageSupplier : suppliers) {
                                    Message message = null;
                                    try {
                                        message = messageSupplier.get();
                                    } catch (Exception e) {
                                        LOGGER.warn("Error in message supplier", e);
                                    }

                                    if (message != null) {
                                        loopControl.incrementProcessed();
                                        try {
                                            connection.postMessage(message);
                                        } catch (Exception e) {
                                            LOGGER.error("Error when writing message", e);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                loopControl.iterationFinished();
            }
        }

        public void shutdown() {
            shutdown = true;
        }
    }

    /**
     * Controls the amount of time to sleep after each iteration of the main message processing loop.
     * It implements an adaptive strategy and increases the amount of time for the dispatcher to sleep
     * after each iteration during which no messages were either received or sent.
     * This strategy greatly reduces CPU load when there is little network activity.
     */
    private static class LoopControl {

        private ReentrantLock lock;
        private Condition timer;

        private long maxTimeToSleep;
        private int messagesProcessed;
        private long timeToSleep;

        LoopControl(long maxTimeToSleep) {
            this.maxTimeToSleep = maxTimeToSleep;

            lock = new ReentrantLock();
            timer = lock.newCondition();
        }

        void incrementProcessed() {
            messagesProcessed++;
            timeToSleep = 1;
        }

        void iterationFinished() {
            if (messagesProcessed == 0) {
                lock.lock();
                try {
                    timer.await(timeToSleep, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ignore
                } finally {
                    lock.unlock();
                }
                timeToSleep = Math.min(timeToSleep << 1, maxTimeToSleep);
            }
            messagesProcessed = 0;
        }
    }

    private boolean isSupportedAndActive(TorrentId torrentId) {
        Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
        // it's OK if descriptor is not present -- torrent might be being fetched at the time
        return torrentRegistry.getTorrentIds().contains(torrentId)
                && (!descriptor.isPresent() || descriptor.get().isActive());
    }

    @Override
    public synchronized void addMessageConsumer(Peer sender, Consumer<Message> messageConsumer) {
        Collection<Consumer<Message>> peerConsumers = consumers.get(sender);
        if (peerConsumers == null) {
            peerConsumers = ConcurrentHashMap.newKeySet();
            consumers.put(sender, peerConsumers);
        }
        peerConsumers.add(messageConsumer);
    }

    @Override
    public synchronized void addMessageSupplier(Peer recipient, Supplier<Message> messageSupplier) {
        Collection<Supplier<Message>> peerSuppliers = suppliers.get(recipient);
        if (peerSuppliers == null) {
            peerSuppliers = ConcurrentHashMap.newKeySet();
            suppliers.put(recipient, peerSuppliers);
        }
        peerSuppliers.add(messageSupplier);
    }
}
