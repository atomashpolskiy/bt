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

import bt.metainfo.TorrentId;
import bt.protocol.Message;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private final Map<TorrentId, Map<Peer, Collection<ConnectionMessageConsumer>>> consumers;
    private final Map<TorrentId, Map<Peer, Collection<ConnectionMessageSupplier>>> suppliers;

    private final TorrentRegistry torrentRegistry;
    private final Object modificationLock;

    @Inject
    public MessageDispatcher(IRuntimeLifecycleBinder lifecycleBinder,
                             IPeerConnectionPool pool,
                             TorrentRegistry torrentRegistry,
                             Config config) {

        this.consumers = new ConcurrentHashMap<>();
        this.suppliers = new ConcurrentHashMap<>();
        this.torrentRegistry = torrentRegistry;
        this.modificationLock = new Object();

        initializeMessageLoop(lifecycleBinder, pool, config);
    }

    private void initializeMessageLoop(IRuntimeLifecycleBinder lifecycleBinder,
                                       IPeerConnectionPool pool,
                                       Config config) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.message-dispatcher"));
        LoopControl loopControl = new LoopControl(config.getMaxMessageProcessingInterval().toMillis());
        MessageDispatchingLoop loop = new MessageDispatchingLoop(pool, loopControl);
        lifecycleBinder.onStartup("Initialize message dispatcher", () -> executor.execute(loop));
        lifecycleBinder.onShutdown("Shutdown message dispatcher", () -> {
            try {
                loop.shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private class MessageDispatchingLoop implements Runnable {
        private final IPeerConnectionPool pool;
        private final LoopControl loopControl;

        private volatile boolean shutdown;

        MessageDispatchingLoop(IPeerConnectionPool pool, LoopControl loopControl) {
            this.pool = pool;
            this.loopControl = loopControl;
        }

        @Override
        public void run() {
            while (!shutdown) {
                if (!consumers.isEmpty()) {
                    Iterator<Map.Entry<TorrentId, Map<Peer, Collection<ConnectionMessageConsumer>>>> iter = consumers.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<TorrentId, Map<Peer, Collection<ConnectionMessageConsumer>>> consumerMapByTorrent = iter.next();
                        Map<Peer, Collection<ConnectionMessageConsumer>> consumerMapByPeer = consumerMapByTorrent.getValue();
                        if (consumerMapByPeer.isEmpty()) {
                            synchronized (modificationLock) {
                                if (consumerMapByPeer.isEmpty()) {
                                    iter.remove();
                                }
                            }
                        }
                        TorrentId torrentId = consumerMapByTorrent.getKey();
                        if (torrentRegistry.isSupportedAndActive(torrentId)) {
                            processConsumerMap(torrentId);
                        }
                    }
                }

                if (!suppliers.isEmpty()) {
                    Iterator<Map.Entry<TorrentId, Map<Peer, Collection<ConnectionMessageSupplier>>>> iter = suppliers.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<TorrentId, Map<Peer, Collection<ConnectionMessageSupplier>>> supplierMapByTorrent = iter.next();
                        Map<Peer, Collection<ConnectionMessageSupplier>> supplierMapByPeer = supplierMapByTorrent.getValue();
                        if (supplierMapByPeer.isEmpty()) {
                            synchronized (modificationLock) {
                                if (supplierMapByPeer.isEmpty()) {
                                    iter.remove();
                                }
                            }
                        }
                        TorrentId torrentId = supplierMapByTorrent.getKey();
                        if (torrentRegistry.isSupportedAndActive(torrentId)) {
                            processSupplierMap(torrentId);
                        }
                    }
                }

                loopControl.iterationFinished();
            }
        }

        private void processConsumerMap(TorrentId torrentId) {
            Map<Peer, Collection<ConnectionMessageConsumer>> consumerMap = consumers.get(torrentId);
            if (consumerMap.isEmpty()) {
                return;
            }

            Iterator<Map.Entry<Peer, Collection<ConnectionMessageConsumer>>> iter = consumerMap.entrySet().iterator();
            while (iter.hasNext()) {
                Collection<ConnectionMessageConsumer> peerConsumers = iter.next().getValue();
                if (peerConsumers.isEmpty()) {
                    synchronized (modificationLock) {
                        if (peerConsumers.isEmpty()) {
                            iter.remove();
                        }
                    }
                } else {
                    ConnectionKey connectionKey = peerConsumers.iterator().next().getConnectionKey();
                    PeerConnection connection = pool.getConnection(connectionKey);
                    if (connection != null && !connection.isClosed()) {
                        Message message;
                        for (;;) {
                            try {
                                message = connection.readMessageNow();
                            } catch (Exception e) {
                                LOGGER.error("Error when reading message from peer connection: " + connectionKey.getPeer(), e);
                                break;
                            }

                            if (message == null) {
                                break;
                            }

                            loopControl.incrementProcessed();
                            for (ConnectionMessageConsumer consumer : peerConsumers) {
                                try {
                                    consumer.getConsumer().accept(message);
                                } catch (Exception e) {
                                    LOGGER.warn("Error in message consumer", e);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void processSupplierMap(TorrentId torrentId) {
            Map<Peer, Collection<ConnectionMessageSupplier>> supplierMap = suppliers.get(torrentId);
            if (supplierMap.isEmpty()) {
                return;
            }

            Iterator<Map.Entry<Peer, Collection<ConnectionMessageSupplier>>> iter = supplierMap.entrySet().iterator();
            while (iter.hasNext()) {
                Collection<ConnectionMessageSupplier> peerSuppliers = iter.next().getValue();
                if (peerSuppliers.isEmpty()) {
                    synchronized (modificationLock) {
                        if (peerSuppliers.isEmpty()) {
                            iter.remove();
                        }
                    }
                } else {
                    ConnectionKey connectionKey = peerSuppliers.iterator().next().getConnectionKey();
                    PeerConnection connection = pool.getConnection(connectionKey);
                    if (connection != null && !connection.isClosed()) {
                        for (ConnectionMessageSupplier messageSupplier : peerSuppliers) {
                            Message message;
                            try {
                                message = messageSupplier.getSupplier().get();
                            } catch (Exception e) {
                                LOGGER.warn("Error in message supplier", e);
                                continue;
                            }

                            if (message == null) {
                                continue;
                            }

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

    @Override
    public void addMessageConsumer(TorrentId torrentId, Peer sender, Consumer<Message> messageConsumer) {
        synchronized (modificationLock) {
            Map<Peer, Collection<ConnectionMessageConsumer>> consumerMapByPeer =
                    consumers.computeIfAbsent(torrentId, it -> new ConcurrentHashMap<>());

            Collection<ConnectionMessageConsumer> peerConsumers =
                    consumerMapByPeer.computeIfAbsent(sender, it -> ConcurrentHashMap.newKeySet());

            ConnectionKey connectionKey = new ConnectionKey(sender, torrentId);
            peerConsumers.add(new ConnectionMessageConsumer(connectionKey, messageConsumer));
        }
    }

    @Override
    public void addMessageSupplier(TorrentId torrentId, Peer recipient, Supplier<Message> messageSupplier) {
        synchronized (modificationLock) {
            Map<Peer, Collection<ConnectionMessageSupplier>> supplierMapByPeer =
                    suppliers.computeIfAbsent(torrentId, it -> new ConcurrentHashMap<>());

            Collection<ConnectionMessageSupplier> peerSuppliers =
                    supplierMapByPeer.computeIfAbsent(recipient, it -> ConcurrentHashMap.newKeySet());

            ConnectionKey connectionKey = new ConnectionKey(recipient, torrentId);
            peerSuppliers.add(new ConnectionMessageSupplier(connectionKey, messageSupplier));
        }
    }

    private static class ConnectionMessageConsumer {
        private final ConnectionKey connectionKey;
        private final Consumer<Message> consumer;

        ConnectionMessageConsumer(ConnectionKey connectionKey, Consumer<Message> consumer) {
            this.connectionKey = connectionKey;
            this.consumer = consumer;
        }

        ConnectionKey getConnectionKey() {
            return connectionKey;
        }

        Consumer<Message> getConsumer() {
            return consumer;
        }
    }

    private static class ConnectionMessageSupplier {
        private final ConnectionKey connectionKey;
        private final Supplier<Message> supplier;

        ConnectionMessageSupplier(ConnectionKey connectionKey, Supplier<Message> supplier) {
            this.connectionKey = connectionKey;
            this.supplier = supplier;
        }

        ConnectionKey getConnectionKey() {
            return connectionKey;
        }

        Supplier<Message> getSupplier() {
            return supplier;
        }
    }
}
