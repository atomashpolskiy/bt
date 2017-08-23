package bt.event;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class EventBus implements EventSink, EventSource {

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
            listeners.add(listener);
        } finally {
            eventLock.writeLock().unlock();
        }
    }
}
