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

package bt.event;

import bt.data.Bitfield;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Basic implementation of event bus, that connects event producers and listeners.
 * In this implementation all events are delivered synchronously.
 *
 * @since 1.5
 */
public class EventBus implements EventSink, EventSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);

    private final ConcurrentMap<Class<? extends BaseEvent>, Collection<Consumer<? extends BaseEvent>>> listeners;

    private final ReentrantReadWriteLock eventLock;

    private long idSequence;

    public EventBus() {
        this.listeners = new ConcurrentHashMap<>();
        this.eventLock = new ReentrantReadWriteLock();
    }

    @Override
    public synchronized void firePeerDiscovered(TorrentId torrentId, Peer peer) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerDiscoveredEvent.class)) {
            long id = nextId();
            fireEvent(new PeerDiscoveredEvent(id, timestamp, torrentId, peer));
        }
    }

    @Override
    public synchronized void firePeerConnected(TorrentId torrentId, Peer peer) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerConnectedEvent.class)) {
            long id = nextId();
            fireEvent(new PeerConnectedEvent(id, timestamp, torrentId, peer));
        }
    }

    @Override
    public synchronized void firePeerDisconnected(TorrentId torrentId, Peer peer) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerDisconnectedEvent.class)) {
            long id = nextId();
            fireEvent(new PeerDisconnectedEvent(id, timestamp, torrentId, peer));
        }
    }

    @Override
    public void firePeerBitfieldUpdated(TorrentId torrentId, Peer peer, Bitfield bitfield) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerBitfieldUpdatedEvent.class)) {
            long id = nextId();
            fireEvent(new PeerBitfieldUpdatedEvent(id, timestamp, torrentId, peer, bitfield));
        }
    }

    @Override
    public void fireTorrentStarted(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(TorrentStartedEvent.class)) {
            long id = nextId();
            fireEvent(new TorrentStartedEvent(id, timestamp, torrentId));
        }
    }

    @Override
    public void fireTorrentStopped(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(TorrentStoppedEvent.class)) {
            long id = nextId();
            fireEvent(new TorrentStoppedEvent(id, timestamp, torrentId));
        }
    }

    private boolean hasListeners(Class<? extends BaseEvent> eventType) {
        Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(eventType);
        return listeners != null && !listeners.isEmpty();
    }

    private synchronized long nextId() {
        return ++idSequence;
    }

    private <E extends BaseEvent> void fireEvent(E event) {
        eventLock.readLock().lock();
        try {
            Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(event.getClass());
            if (LOGGER.isTraceEnabled()) {
                int count = (listeners == null) ? 0 : listeners.size();
                LOGGER.trace("Firing event: {}. Listeners count: {}", event, count);
            }
            if (listeners != null && !listeners.isEmpty()) {
                for (Consumer<? extends BaseEvent> listener : listeners) {
                    @SuppressWarnings("unchecked")
                    Consumer<E> _listener = (Consumer<E>) listener;
                    _listener.accept(event);
                }
            }
        } finally {
            eventLock.readLock().unlock();
        }
    }

    @Override
    public EventSource onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener) {
        addListener(PeerDiscoveredEvent.class, listener);
        return this;
    }

    @Override
    public EventSource onPeerConnected(Consumer<PeerConnectedEvent> listener) {
        addListener(PeerConnectedEvent.class, listener);
        return this;
    }

    @Override
    public EventSource onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener) {
        addListener(PeerDisconnectedEvent.class, listener);
        return this;
    }

    @Override
    public EventSource onPeerBitfieldUpdated(Consumer<PeerBitfieldUpdatedEvent> listener) {
        addListener(PeerBitfieldUpdatedEvent.class, listener);
        return this;
    }

    @Override
    public EventSource onTorrentStarted(Consumer<TorrentStartedEvent> listener) {
        addListener(TorrentStartedEvent.class, listener);
        return this;
    }

    @Override
    public EventSource onTorrentStopped(Consumer<TorrentStoppedEvent> listener) {
        addListener(TorrentStoppedEvent.class, listener);
        return this;
    }

    private <E extends BaseEvent> void addListener(Class<E> eventType, Consumer<E> listener) {
        Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(eventType);
        if (listeners == null) {
            listeners = ConcurrentHashMap.newKeySet();
            Collection<Consumer<? extends BaseEvent>> existing = this.listeners.putIfAbsent(eventType, listeners);
            if (existing != null) {
                listeners = existing;
            }
        }

        eventLock.writeLock().lock();
        try {
            Consumer<E> safeListener = event -> {
                try {
                    listener.accept(event);
                } catch (Exception ex) {
                    LOGGER.error("Listener invocation failed", ex);
                }
            };
            listeners.add(safeListener);
        } finally {
            eventLock.writeLock().unlock();
        }
    }
}
