/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import static java.util.Collections.emptyMap;

/**
 * Default single-threaded message dispatcher implementation.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class MessageDispatcher implements IMessageDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatcher.class);

    private final Map<TorrentId, Map<ConnectionKey, Consumer<Message>>> consumers;
    private final Map<TorrentId, Map<ConnectionKey, Supplier<Message>>> suppliers;

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
        String threadName = String.format("%d.bt.net.message-dispatcher", config.getAcceptorPort());
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
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
                Iterator<Map.Entry<TorrentId, Map<ConnectionKey, Consumer<Message>>>> consumerIter = consumers.entrySet().iterator();
                while (consumerIter.hasNext()) {
                    Map.Entry<TorrentId, Map<ConnectionKey, Consumer<Message>>> consumerMapByTorrent = consumerIter.next();
                    Map<ConnectionKey, Consumer<Message>> consumerMapByPeer = consumerMapByTorrent.getValue();
                    // remove inactive torrents with no peers
                    if (consumerMapByPeer.isEmpty()) {
                        synchronized (modificationLock) {
                            if (consumerMapByPeer.isEmpty()) {
                                consumerIter.remove();
                                continue;
                            }
                        }
                    }
                    TorrentId torrentId = consumerMapByTorrent.getKey();
                    if (torrentRegistry.isSupportedAndActive(torrentId)) {
                        processConsumerMap(consumerMapByPeer);
                    }
                }

                Iterator<Map.Entry<TorrentId, Map<ConnectionKey, Supplier<Message>>>> supplierIter = suppliers.entrySet().iterator();
                while (supplierIter.hasNext()) {
                    Map.Entry<TorrentId, Map<ConnectionKey, Supplier<Message>>> supplierMapByTorrent = supplierIter.next();
                    Map<ConnectionKey, Supplier<Message>> supplierMapByPeer = supplierMapByTorrent.getValue();
                    if (supplierMapByPeer.isEmpty()) {
                        synchronized (modificationLock) {
                            // remove inactive torrents with no peers
                            if (supplierMapByPeer.isEmpty()) {
                                supplierIter.remove();
                                continue;
                            }
                        }
                    }
                    TorrentId torrentId = supplierMapByTorrent.getKey();
                    if (torrentRegistry.isSupportedAndActive(torrentId)) {
                        processSupplierMap(supplierMapByPeer);
                    }
                }

                try {
                    loopControl.iterationFinished();
                } catch (InterruptedException e) {
                    LOGGER.info("Wait interrupted, shutting down...");
                    return;
                }
            }
        }

        private void processConsumerMap(Map<ConnectionKey, Consumer<Message>> consumerMap) {
            Iterator<Map.Entry<ConnectionKey, Consumer<Message>>> iter = consumerMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ConnectionKey, Consumer<Message>> e = iter.next();
                ConnectionKey connectionKey = e.getKey();
                Consumer<Message> peerConsumer = e.getValue();
                PeerConnection connection = pool.getConnection(connectionKey);
                if (isActive(connection)) {
                    Message message;
                    for (;;) {
                        try {
                            message = connection.readMessageNow();
                        } catch (Exception ex) {
                            LOGGER.error("Error when reading message from peer connection: " + connectionKey.getPeer(),
                                    ex);
                            break;
                        }

                        if (message == null) {
                            break;
                        }

                        loopControl.incrementProcessed();
                        try {
                            peerConsumer.accept(message);
                        } catch (Exception ex) {
                            LOGGER.warn("Error in message consumer", ex);
                        }
                    }
                } else {
                    removeInactiveConnection(connectionKey, iter);
                }
            }
        }

        private void removeInactiveConnection(ConnectionKey connectionKey, Iterator<?> iter) {
            // Synchronized check to ensure that the connection is not reestablished and added before it is removed.
            synchronized (modificationLock) {
                PeerConnection connection = pool.getConnection(connectionKey);
                if (!isActive(connection)) {
                    iter.remove();
                }
            }
        }

        private void processSupplierMap(Map<ConnectionKey, Supplier<Message>> supplierMap) {
            Iterator<Map.Entry<ConnectionKey, Supplier<Message>>> iter = supplierMap.entrySet().iterator();
            while (iter.hasNext())
            {
                Map.Entry<ConnectionKey, Supplier<Message>> e = iter.next();
                ConnectionKey connectionKey = e.getKey();
                Supplier<Message> peerSupplier = e.getValue();
                PeerConnection connection = pool.getConnection(connectionKey);
                if (isActive(connection))
                {
                    Message message;
                    try {
                        message = peerSupplier.get();
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
                } else {
                    removeInactiveConnection(connectionKey, iter);
                }
            }
        }

        private boolean isActive(PeerConnection connection) {
            return connection != null && !connection.isClosed();
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

        private final long maxTimeToSleep;
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

        synchronized void iterationFinished() throws InterruptedException {
            if (messagesProcessed > 0) {
                reset();
            } else {
                wait(timeToSleep);

                if (timeToSleep < maxTimeToSleep) {
                    timeToSleep = Math.min(timeToSleep << 1, maxTimeToSleep);
                } else {
                    timeToSleep = maxTimeToSleep;
                }
            }
        }
    }

    @Override
    public void setConnectionMessageConsumerAndSupplier(ConnectionKey connectionKey, Consumer<Message> messageConsumer,
            Supplier<Message> messageSupplier)
    {
        synchronized (modificationLock)
        {
            Map<ConnectionKey, Consumer<Message>> consumerMapByPeer = consumers.computeIfAbsent(
                    connectionKey.getTorrentId(), t -> new ConcurrentHashMap<>());
            consumerMapByPeer.put(connectionKey, messageConsumer);

            Map<ConnectionKey, Supplier<Message>> supplierMapByPeer = suppliers.computeIfAbsent(
                    connectionKey.getTorrentId(), t -> new ConcurrentHashMap<>());
            supplierMapByPeer.put(connectionKey, messageSupplier);
        }
    }
}
