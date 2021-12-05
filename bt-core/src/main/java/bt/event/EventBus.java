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

package bt.event;

import bt.data.Bitfield;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.ConnectionKey;
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

    private final ConcurrentMap<TorrentId, ConcurrentMap<Class<? extends BaseEvent>, Collection<Consumer<? extends BaseEvent>>>> listenersOnTorrent;

    private final ReentrantReadWriteLock eventLock;

    private long idSequence;

    public EventBus() {
        this.listeners = new ConcurrentHashMap<>();
        this.listenersOnTorrent = new ConcurrentHashMap<>();
        this.eventLock = new ReentrantReadWriteLock();
    }

    @Override
    public synchronized void firePeerDiscovered(TorrentId torrentId, Peer peer) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerDiscoveredEvent.class, torrentId)) {
            long id = nextId();
            fireEvent(new PeerDiscoveredEvent(id, timestamp, torrentId, peer), torrentId);
        }
    }

    @Override
    public synchronized void firePeerConnected(ConnectionKey connectionKey) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerConnectedEvent.class, connectionKey.getTorrentId())) {
            long id = nextId();
            fireEvent(new PeerConnectedEvent(id, timestamp, connectionKey), connectionKey.getTorrentId());
        }
    }

    @Override
    public synchronized void firePeerDisconnected(ConnectionKey connectionKey) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerDisconnectedEvent.class, connectionKey.getTorrentId())) {
            long id = nextId();
            fireEvent(new PeerDisconnectedEvent(id, timestamp, connectionKey), connectionKey.getTorrentId());
        }
    }

    @Override
    public void firePeerBitfieldUpdated(TorrentId torrentId, ConnectionKey connectionKey, Bitfield bitfield) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PeerBitfieldUpdatedEvent.class, torrentId)) {
            long id = nextId();
            fireEvent(new PeerBitfieldUpdatedEvent(id, timestamp, connectionKey, bitfield), torrentId);
        }
    }

    @Override
    public void fireTorrentStarted(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(TorrentStartedEvent.class, torrentId)) {
            long id = nextId();
            fireEvent(new TorrentStartedEvent(id, timestamp, torrentId), torrentId);
        }
    }

    @Override
    public void fireMetadataAvailable(TorrentId torrentId, Torrent torrent) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(MetadataAvailableEvent.class, torrentId)) {
            long id = nextId();
            fireEvent(new MetadataAvailableEvent(id, timestamp, torrentId, torrent), torrentId);
        }
    }

    @Override
    public void fireTorrentStopped(TorrentId torrentId) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(TorrentStoppedEvent.class, torrentId)) {
            long id = nextId();
            fireEvent(new TorrentStoppedEvent(id, timestamp, torrentId), torrentId);
        }
        if (torrentId != null) {
            listenersOnTorrent.remove(torrentId);
        }
    }

    @Override
    public void firePieceVerified(TorrentId torrentId, int pieceIndex) {
        long timestamp = System.currentTimeMillis();
        if (hasListeners(PieceVerifiedEvent.class, torrentId)) {
            long id = nextId();
            fireEvent(new PieceVerifiedEvent(id, timestamp, torrentId, pieceIndex), torrentId);
        }
    }

    private boolean hasListeners(Class<? extends BaseEvent> eventType, TorrentId torrentId) {
        Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(eventType);
        if (listeners != null && !listeners.isEmpty()) {
            return true;
        }

        if (torrentId == null) {
            return false;
        }

        ConcurrentMap<Class<? extends BaseEvent>, Collection<Consumer<? extends BaseEvent>>> map = this.listenersOnTorrent.get(torrentId);
        if (map == null) {
            return false;
        }

        listeners = map.get(eventType);
        return listeners != null && !listeners.isEmpty();
    }

    private synchronized long nextId() {
        return ++idSequence;
    }

    private <E extends BaseEvent> void fireEvent(E event, TorrentId torrentId) {
        eventLock.readLock().lock();
        try {
            Collection<Consumer<? extends BaseEvent>> listeners = this.listeners.get(event.getClass());
            doFireEvent(event, listeners, "Firing event: {}. General Listeners count: {}");
            if (torrentId != null) {
                ConcurrentMap<Class<? extends BaseEvent>, Collection<Consumer<? extends BaseEvent>>> map = this.listenersOnTorrent.get(torrentId);
                if (map != null) {
                    listeners = map.get(event.getClass());
                    doFireEvent(event, listeners, "Firing event: {}. Torrent Listeners count: {}");
                }
            }
        } finally {
            eventLock.readLock().unlock();
        }
    }

    private <E extends BaseEvent> void doFireEvent(E event, Collection<Consumer<? extends BaseEvent>> listeners, String s) {
        if (LOGGER.isTraceEnabled()) {
            int count = (listeners == null) ? 0 : listeners.size();
            LOGGER.trace(s, event, count);
        }
        if (listeners != null && !listeners.isEmpty()) {
            for (Consumer<? extends BaseEvent> listener : listeners) {
                @SuppressWarnings("unchecked")
                Consumer<E> _listener = (Consumer<E>) listener;
                _listener.accept(event);
            }
        }
    }

    @Override
    public EventSource onPeerDiscovered(TorrentId torrentId, Consumer<PeerDiscoveredEvent> listener) {
        addListener(PeerDiscoveredEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onPeerConnected(TorrentId torrentId, Consumer<PeerConnectedEvent> listener) {
        addListener(PeerConnectedEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onPeerDisconnected(TorrentId torrentId, Consumer<PeerDisconnectedEvent> listener) {
        addListener(PeerDisconnectedEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onPeerBitfieldUpdated(TorrentId torrentId, Consumer<PeerBitfieldUpdatedEvent> listener) {
        addListener(PeerBitfieldUpdatedEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onTorrentStarted(TorrentId torrentId, Consumer<TorrentStartedEvent> listener) {
        addListener(TorrentStartedEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onMetadataAvailable(TorrentId torrentId, Consumer<MetadataAvailableEvent> listener) {
        addListener(MetadataAvailableEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onTorrentStopped(TorrentId torrentId, Consumer<TorrentStoppedEvent> listener) {
        addListener(TorrentStoppedEvent.class, torrentId, listener);
        return this;
    }

    @Override
    public EventSource onPieceVerified(TorrentId torrentId, Consumer<PieceVerifiedEvent> listener) {
        addListener(PieceVerifiedEvent.class, torrentId, listener);
        return this;
    }

    private <E extends BaseEvent> void addListener(Class<E> eventType, TorrentId torrentId, Consumer<E> listener) {
        Collection<Consumer<? extends BaseEvent>> listeners;
        if (torrentId == null) {
            listeners = this.listeners.computeIfAbsent(eventType, key -> ConcurrentHashMap.newKeySet());
        } else {
            listeners = this.listenersOnTorrent.computeIfAbsent(torrentId, key -> new ConcurrentHashMap<>())
                    .computeIfAbsent(eventType, key -> ConcurrentHashMap.newKeySet());
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
