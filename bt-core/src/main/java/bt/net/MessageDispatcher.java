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

    private final Map<TorrentId, Map<ConnectionKey, Collection<Consumer<Message>>>> consumers;
    private final Map<TorrentId, Map<ConnectionKey, Collection<Supplier<Message>>>> suppliers;

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
                    Iterator<Map.Entry<TorrentId, Map<ConnectionKey, Collection<Consumer<Message>>>>> iter = consumers.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<TorrentId, Map<ConnectionKey, Collection<Consumer<Message>>>> consumerMapByTorrent = iter.next();
                        Map<ConnectionKey, Collection<Consumer<Message>>> consumerMapByPeer = consumerMapByTorrent.getValue();
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
                    Iterator<Map.Entry<TorrentId, Map<ConnectionKey, Collection<Supplier<Message>>>>> iter = suppliers.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<TorrentId, Map<ConnectionKey, Collection<Supplier<Message>>>> supplierMapByTorrent = iter.next();
                        Map<ConnectionKey, Collection<Supplier<Message>>> supplierMapByPeer = supplierMapByTorrent.getValue();
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
            Map<ConnectionKey, Collection<Consumer<Message>>> consumerMap = consumers.get(torrentId);
            if (consumerMap.isEmpty()) {
                return;
            }

            Iterator<Map.Entry<ConnectionKey, Collection<Consumer<Message>>>> iter = consumerMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ConnectionKey, Collection<Consumer<Message>>> e = iter.next();
                ConnectionKey connectionKey = e.getKey();
                Collection<Consumer<Message>> peerConsumers = e.getValue();
                if (peerConsumers.isEmpty()) {
                    synchronized (modificationLock) {
                        if (peerConsumers.isEmpty()) {
                            iter.remove();
                        }
                    }
                } else {
                    PeerConnection connection = pool.getConnection(connectionKey);
                    if (connection != null && !connection.isClosed()) {
                        Message message;
                        for (;;) {
                            try {
                                message = connection.readMessageNow();
                            } catch (Exception ex) {
                                LOGGER.error("Error when reading message from peer connection: " + connectionKey.getPeer(), ex);
                                break;
                            }

                            if (message == null) {
                                break;
                            }

                            loopControl.incrementProcessed();
                            for (Consumer<Message> consumer : peerConsumers) {
                                try {
                                    consumer.accept(message);
                                } catch (Exception ex) {
                                    LOGGER.warn("Error in message consumer", ex);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void processSupplierMap(TorrentId torrentId) {
            Map<ConnectionKey, Collection<Supplier<Message>>> supplierMap = suppliers.get(torrentId);
            if (supplierMap.isEmpty()) {
                return;
            }

            Iterator<Map.Entry<ConnectionKey, Collection<Supplier<Message>>>> iter = supplierMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ConnectionKey, Collection<Supplier<Message>>> e = iter.next();
                ConnectionKey connectionKey = e.getKey();
                Collection<Supplier<Message>> peerSuppliers = e.getValue();
                if (peerSuppliers.isEmpty()) {
                    synchronized (modificationLock) {
                        if (peerSuppliers.isEmpty()) {
                            iter.remove();
                        }
                    }
                } else {
                    PeerConnection connection = pool.getConnection(connectionKey);
                    if (connection != null && !connection.isClosed()) {
                        for (Supplier<Message> messageSupplier : peerSuppliers) {
                            Message message;
                            try {
                                message = messageSupplier.get();
                            } catch (Exception ex) {
                                LOGGER.warn("Error in message supplier", ex);
                                continue;
                            }

                            if (message == null) {
                                continue;
                            }

                            loopControl.incrementProcessed();
                            try {
                                connection.postMessage(message);
                            } catch (Exception ex) {
                                LOGGER.error("Error when writing message", ex);
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
                    LOGGER.info("Wait interrupted");
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
    public void addMessageConsumer(ConnectionKey connectionKey, Consumer<Message> messageConsumer) {
        synchronized (modificationLock) {
            Map<ConnectionKey, Collection<Consumer<Message>>> consumerMapByPeer =
                    consumers.computeIfAbsent(connectionKey.getTorrentId(), it -> new ConcurrentHashMap<>());

            Collection<Consumer<Message>> peerConsumers =
                    consumerMapByPeer.computeIfAbsent(connectionKey, it -> ConcurrentHashMap.newKeySet());

            peerConsumers.add(messageConsumer);
        }
    }

    @Override
    public void addMessageSupplier(ConnectionKey connectionKey, Supplier<Message> messageSupplier) {
        synchronized (modificationLock) {
            Map<ConnectionKey, Collection<Supplier<Message>>> supplierMapByPeer =
                    suppliers.computeIfAbsent(connectionKey.getTorrentId(), it -> new ConcurrentHashMap<>());

            Collection<Supplier<Message>> peerSuppliers =
                    supplierMapByPeer.computeIfAbsent(connectionKey, it -> ConcurrentHashMap.newKeySet());

            peerSuppliers.add(messageSupplier);
        }
    }
}
