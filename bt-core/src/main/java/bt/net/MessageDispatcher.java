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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
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

    private final AtomicLong idSequence;
    private final Map<TorrentId, Map<ConnectionKey, Map<Long, Consumer<Message>>>> consumers;
    private final Map<TorrentId, Map<ConnectionKey, Map<Long, Supplier<Message>>>> suppliers;

    private final TorrentRegistry torrentRegistry;
    private final Object modificationLock;

    @Inject
    public MessageDispatcher(IRuntimeLifecycleBinder lifecycleBinder,
                             IPeerConnectionPool pool,
                             TorrentRegistry torrentRegistry,
                             Config config) {
        this.idSequence = new AtomicLong();
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
                //noinspection Convert2streamapi
                for (TorrentId torrentId : consumers.keySet()) {
                    if (torrentRegistry.isSupportedAndActive(torrentId)) {
                        processConsumerMap(torrentId);
                    }
                }
                //noinspection Convert2streamapi
                for (TorrentId torrentId : suppliers.keySet()) {
                    if (torrentRegistry.isSupportedAndActive(torrentId)) {
                        processSupplierMap(torrentId);
                    }
                }

                loopControl.iterationFinished();
            }
        }

        private void processConsumerMap(TorrentId torrentId) {
            Map<ConnectionKey, Map<Long, Consumer<Message>>> connectionKeyMap = consumers.get(torrentId);
            if (connectionKeyMap == null) {
                return;
            }
            connectionKeyMap.forEach((connectionKey, consumers) -> {
                PeerConnection connection = pool.getConnection(connectionKey);
                if (connection == null || connection.isClosed()) {
                    return;
                }
                if (consumers.isEmpty()) {
                    return;
                }
                for (; ; ) {
                    final Message message;
                    try {
                        message = connection.readMessageNow();
                    } catch (Exception | AssertionError e) {
                        LOGGER.error("Error when reading message from peer connection: " + connectionKey.getPeer(), e);
                        break;
                    }

                    if (message == null) {
                        break;
                    }

                    loopControl.incrementProcessed();
                    //noinspection StatementWithEmptyBody
                    if (consumers.isEmpty()) {
                        // disconnected. ignore message.
                    }
                    for (Consumer<Message> consumer : consumers.values()) {
                        try {
                            consumer.accept(message);
                        } catch (Exception | AssertionError e) {
                            LOGGER.warn("Error in message consumer", e);
                        }
                    }
                }
            });
        }

        private void processSupplierMap(TorrentId torrentId) {
            Map<ConnectionKey, Map<Long, Supplier<Message>>> connectionKeyMap = suppliers.get(torrentId);
            if (connectionKeyMap == null) {
                return;
            }
            connectionKeyMap.forEach((connectionKey, suppliers) -> {
                PeerConnection connection = pool.getConnection(connectionKey);
                if (connection == null || connection.isClosed()) {
                    return;
                }
                for (Supplier<Message> supplier : suppliers.values()) {
                    final Message message;
                    try {
                        message = supplier.get();
                    } catch (Exception | AssertionError e) {
                        LOGGER.warn("Error in message supplier", e);
                        continue;
                    }

                    if (message == null) {
                        continue;
                    }

                    loopControl.incrementProcessed();
                    try {
                        connection.postMessage(message);
                    } catch (Exception | AssertionError e) {
                        LOGGER.error("Error when writing message", e);
                    }
                }
            });
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
    public long nextId() {
        return idSequence.getAndIncrement();
    }

    @Override
    public void addMessageConsumer(TorrentId torrentId, Peer sender, long id, Consumer<Message> messageConsumer) {
        add(consumers, torrentId, sender, id, messageConsumer);
    }

    @Override
    public void addMessageSupplier(TorrentId torrentId, Peer recipient, long id, Supplier<Message> messageSupplier) {
        add(suppliers, torrentId, recipient, id, messageSupplier);
    }

    @Override
    public void removeMessageConsumer(TorrentId torrentId, Peer sender, long id) {
        remove(consumers, torrentId, sender, id);
    }

    @Override
    public void removeMessageSupplier(TorrentId torrentId, Peer recipient, long id) {
        remove(suppliers, torrentId, recipient, id);
    }

    private <U> void add(Map<TorrentId, Map<ConnectionKey, Map<Long, U>>> map,
                         TorrentId torrentId,
                         Peer peer,
                         long id,
                         U item) {
        synchronized (modificationLock) {
            Map<ConnectionKey, Map<Long, U>> connectionKeyMap =
                    map.computeIfAbsent(torrentId, it -> new ConcurrentHashMap<>());
            ConnectionKey connectionKey = new ConnectionKey(peer, torrentId);
            Map<Long, U> itemMap = connectionKeyMap.computeIfAbsent(connectionKey, it -> new ConcurrentHashMap());
            itemMap.put(id, item);
        }
    }

    private <U> void remove(Map<TorrentId, Map<ConnectionKey, Map<Long, U>>> map,
                            TorrentId torrentId,
                            Peer peer,
                            long id) {
        synchronized (modificationLock) {
            map.computeIfPresent(torrentId, (torrentId1, connectionKeyMap) -> {
                ConnectionKey connectionKey = new ConnectionKey(peer, torrentId1);
                connectionKeyMap.computeIfPresent(connectionKey, (connectionKey1, itemMap) -> {
                    itemMap.remove(id);
                    return itemMap.isEmpty() ? null : itemMap;
                });
                return connectionKeyMap.isEmpty() ? null : connectionKeyMap;
            });
        }
    }
}
