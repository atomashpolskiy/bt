/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.net;

import bt.event.EventSource;
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

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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
                             EventSource eventSource,
                             @PeerConnectionSelector SharedSelector selector,
                             Config config) {

        this.consumers = new ConcurrentHashMap<>();
        this.suppliers = new ConcurrentHashMap<>();
        this.torrentRegistry = torrentRegistry;

        Queue<PeerMessage> messages = new LinkedBlockingQueue<>(); // shared message queue
        initializeMessageLoop(lifecycleBinder, pool, messages, config);
        initializeMessageReceiver(eventSource, pool, selector, torrentRegistry, lifecycleBinder, messages);
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

    private void initializeMessageReceiver(EventSource eventSource,
                                           IPeerConnectionPool pool,
                                           SharedSelector selector,
                                           TorrentRegistry torrentRegistry,
                                           IRuntimeLifecycleBinder lifecycleBinder,
                                           Queue<PeerMessage> messages) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.message-receiver"));
        BiConsumer<Peer, Message> messageSink = (peer, message) ->
                messages.add(new PeerMessage(Objects.requireNonNull(peer), Objects.requireNonNull(message)));
        MessageReceivingLoop loop = new MessageReceivingLoop(eventSource, pool, selector, messageSink, torrentRegistry);
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

        private static final int NO_OPS = 0;

        private final EventSource eventSource;
        private final IPeerConnectionPool pool;
        private final SharedSelector selector;
        private final BiConsumer<Peer, Message> messageSink;
        private final TorrentRegistry torrentRegistry;

        private volatile boolean shutdown;

        MessageReceivingLoop(EventSource eventSource,
                             IPeerConnectionPool pool,
                             SharedSelector selector,
                             BiConsumer<Peer, Message> messageSink,
                             TorrentRegistry torrentRegistry) {
            this.eventSource = eventSource;
            this.pool = pool;
            this.selector = selector;
            this.messageSink = messageSink;
            this.torrentRegistry = torrentRegistry;
        }

        private synchronized void onPeerConnected(Peer peer) {
            PeerConnection connection = pool.getConnection(peer);
            if (connection != null) {
                try {
                    // drain all buffers before registering
                    Message message;
                    while ((message = connection.readMessageNow()) != null) {
                        messageSink.accept(peer, message);
                    }
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Registering new connection for peer: {}", peer);
                    }
                    registerConnection(connection);
                } catch (Exception e) {
                    LOGGER.error("Failed to register new connection for peer: " + peer, e);
                }
            }
        }

        private void registerConnection(PeerConnection connection) {
            if (connection instanceof SocketPeerConnection) {
                SocketChannel channel = ((SocketPeerConnection)connection).getChannel();
                // use atomic wakeup-and-register to prevent blocking of registration,
                // if selection is resumed before call to register is performed
                // (there is a race between the message receiving loop and current thread)
                // TODO: move this to the main loop instead?
                int interestOps = getInterestOps(connection.getTorrentId());
                selector.wakeupAndRegister(channel, interestOps, connection);
            }
        }

        private int getInterestOps(TorrentId torrentId) {
            Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
            boolean active = descriptor.isPresent() && descriptor.get().isActive();
            return active ? SelectionKey.OP_READ : NO_OPS;
        }

        private synchronized void onTorrentStarted(TorrentId torrentId) {
            pool.visitConnections(torrentId, connection -> {
                Peer peer = connection.getRemotePeer();
                try {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Activating connection for peer: {}", peer);
                    }
                    updateInterestOps(connection, SelectionKey.OP_READ);
                } catch (Exception e) {
                    LOGGER.error("Failed to activate connection for peer: " + peer, e);
                }
            });
        }

        private synchronized void onTorrentStopped(TorrentId torrentId) {
            pool.visitConnections(torrentId, connection -> {
                Peer peer = connection.getRemotePeer();
                try {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("De-activating connection for peer: {}", peer);
                    }
                    updateInterestOps(connection, NO_OPS);
                } catch (Exception e) {
                    LOGGER.error("Failed to de-activate connection for peer: " + peer, e);
                }
            });
        }

        private void updateInterestOps(PeerConnection connection, int interestOps) {
            if (connection instanceof SocketPeerConnection) {
                SocketChannel channel = ((SocketPeerConnection)connection).getChannel();
                selector.keyFor(channel).ifPresent(key -> {
                    // synchronizing on the selection key,
                    // as we will be using it in a separate, message receiving thread
                    synchronized (key) {
                        key.interestOps(interestOps);
                    }
                });
            }
        }

        @Override
        public void run() {
            eventSource.onPeerConnected(e -> onPeerConnected(e.getPeer()));
            eventSource.onTorrentStarted(e -> onTorrentStarted(e.getTorrentId()));
            eventSource.onTorrentStopped(e -> onTorrentStopped(e.getTorrentId()));

            while (!shutdown) {
                if (!selector.isOpen()) {
                    LOGGER.info("Selector is closed, stopping...");
                    break;
                }

                try {
                    // wakeup periodically to check if there are unprocessed keys left
                    long t1 = System.nanoTime();
                    long timeToBlockMillis = 1000;
                    while (selector.select(timeToBlockMillis) == 0) {
                        Thread.yield();
                        long t2 = System.nanoTime();
                        // check that the selection timeout period is expired, before dealing with unprocessed keys;
                        // it could be a call to wake up, that made the select() return,
                        // and we don't want to perform extra work on each spin iteration
                        if ((t2 - t1 >= timeToBlockMillis * 1000) && !selector.selectedKeys().isEmpty()) {
                            // try to deal with unprocessed keys, left from the previous iteration
                            break;
                        }
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
        private boolean processKey(final SelectionKey key) {
            PeerConnection connection;
            Peer peer;

            // synchronizing on the selection key,
            // as we will be updating it in a separate, event-listening thread
            synchronized (key) {
                Object obj = key.attachment();
                if (obj == null || !(obj instanceof PeerConnection)) {
                    LOGGER.warn("Unexpected attachment in selection key: {}. Skipping..", obj);
                    return false;
                }

                connection = (PeerConnection) obj;
                peer = connection.getRemotePeer();
                if (!key.isValid() || !key.isReadable()) {
                    LOGGER.warn("Selected connection for peer {}, but the key is cancelled or read op is not indicated. Skipping..", peer);
                    return false;
                }
            }

            if (connection.isClosed()) {
                LOGGER.warn("Selected connection for peer {}, but the connection has already been closed. Skipping..", peer);
                throw new RuntimeException("Connection closed");
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
                boolean error = false;
                try {
                    message = connection.readMessageNow();
                } catch (EOFException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Received EOF from peer: {}. Disconnecting...", peer);
                    }
                    error = true;
                } catch (IOException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("I/O error when reading message from peer: {}. Reason: {} ({})",
                                peer, e.getClass().getName(), e.getMessage());
                    }
                    error = true;
                } catch (Exception e) {
                    LOGGER.error("Unexpected error when reading message from peer: " + peer, e);
                    error = true;
                }
                if (error) {
                    connection.closeQuietly();
                    key.cancel();
                    break;
                } else if (message == null) {
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

        private long maxTimeToSleep;
        private int messagesProcessed;
        private long timeToSleep;

        LoopControl(long maxTimeToSleep) {
            this.maxTimeToSleep = maxTimeToSleep;
            reset();
        }

        private void reset() {
            messagesProcessed = 0;
            timeToSleep = 1;
        }

        void incrementProcessed() {
            messagesProcessed++;
        }

        synchronized void iterationFinished() {
            if (messagesProcessed > 0) {
                reset();
            } else {
                try {
                    wait(timeToSleep);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpectedly interrupted", e);
                }

                if (timeToSleep < maxTimeToSleep) {
                    timeToSleep = Math.min(timeToSleep << 1, maxTimeToSleep);
                } else {
                    timeToSleep = maxTimeToSleep;
                }
            }
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
