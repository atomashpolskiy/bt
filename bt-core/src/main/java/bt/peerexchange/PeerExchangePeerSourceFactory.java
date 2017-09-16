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

package bt.peerexchange;

import bt.BtException;
import bt.event.EventSource;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.peer.PeerSource;
import bt.peer.PeerSourceFactory;
import bt.protocol.Message;
import bt.protocol.extended.ExtendedHandshake;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.MessageContext;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class PeerExchangePeerSourceFactory implements PeerSourceFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerExchangePeerSourceFactory.class);

    private static final Duration CLEANER_INTERVAL = Duration.ofSeconds(37);

    private Map<TorrentId, PeerExchangePeerSource> peerSources;

    private Map<TorrentId, Queue<PeerEvent>> peerEvents;
    private ReentrantReadWriteLock rwLock;

    private Set<Peer> peers;
    private Map<Peer, Long> lastSentPEXMessage;

    private Duration minMessageInterval;
    private int minEventsPerMessage;
    private int maxEventsPerMessage;

    @Inject
    public PeerExchangePeerSourceFactory(EventSource eventSource,
                                         IRuntimeLifecycleBinder lifecycleBinder,
                                         PeerExchangeConfig config) {
        this.peerSources = new ConcurrentHashMap<>();
        this.peerEvents = new ConcurrentHashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
        this.peers = ConcurrentHashMap.newKeySet();
        this.lastSentPEXMessage = new ConcurrentHashMap<>();
        this.minMessageInterval = config.getMinMessageInterval();
        this.minEventsPerMessage = config.getMinEventsPerMessage();
        this.maxEventsPerMessage = config.getMaxEventsPerMessage();

        eventSource.onPeerConnected(e -> onPeerConnected(e.getTorrentId(), e.getPeer()))
                .onPeerDisconnected(e -> onPeerDisconnected(e.getTorrentId(), e.getPeer()));

        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "bt.peerexchange.cleaner"));
        lifecycleBinder.onStartup("Schedule periodic cleanup of PEX messages", () -> executor.scheduleAtFixedRate(
                new Cleaner(), CLEANER_INTERVAL.toMillis(), CLEANER_INTERVAL.toMillis(), TimeUnit.MILLISECONDS));
        lifecycleBinder.onShutdown("Shutdown PEX cleanup scheduler", executor::shutdownNow);
    }

    private void onPeerConnected(TorrentId torrentId, Peer peer) {
        getPeerEvents(torrentId).add(PeerEvent.added(peer));
    }

    private void onPeerDisconnected(TorrentId torrentId, Peer peer) {
        getPeerEvents(torrentId).add(PeerEvent.dropped(peer));
        peers.remove(peer);
        lastSentPEXMessage.remove(peer);
    }

    private Queue<PeerEvent> getPeerEvents(TorrentId torrentId) {
        Queue<PeerEvent> events = peerEvents.get(torrentId);
        if (events == null) {
            events = new PriorityBlockingQueue<>();
            Queue<PeerEvent> existing = peerEvents.putIfAbsent(torrentId, events);
            if (existing != null) {
                events = existing;
            }
        }
        return events;
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        return getOrCreatePeerSource(torrentId);
    }

    private PeerExchangePeerSource getOrCreatePeerSource(TorrentId torrentId) {
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

    @Consumes
    public void consume(ExtendedHandshake handshake, MessageContext messageContext) {
        if (handshake.getSupportedMessageTypes().contains("ut_pex")) {
            // TODO: peer may eventually turn off the PEX extension
            // moreover the extended handshake message type map is additive,
            // so we can't learn about the peer turning off extensions solely from the message
            peers.add(messageContext.getPeer());
        }
    }

    @Consumes
    public void consume(PeerExchange message, MessageContext messageContext) {
        messageContext.getTorrentId().ifPresent(torrentId ->
                getOrCreatePeerSource(torrentId).addMessage(message));
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext messageContext) {
        Peer peer = messageContext.getPeer();
        long currentTime = System.currentTimeMillis();
        long lastSentPEXMessageToPeer = lastSentPEXMessage.getOrDefault(peer, 0L);

        if (peers.contains(peer) && (currentTime - lastSentPEXMessageToPeer) >= minMessageInterval.toMillis()) {
            List<PeerEvent> events = new ArrayList<>();

            rwLock.readLock().lock();
            try {
                Queue<PeerEvent> torrentPeerEvents = getPeerEvents(messageContext.getTorrentId().get());
                for (PeerEvent event : torrentPeerEvents) {
                    if (event.getInstant() - lastSentPEXMessageToPeer >= 0) {
                        if (!event.getPeer().equals(peer)) {
                            events.add(event);
                        }
                    }
                    if (events.size() >= maxEventsPerMessage) {
                        break;
                    }
                }
            } finally {
                rwLock.readLock().unlock();
            }

            if (events.size() >= minEventsPerMessage) {
                lastSentPEXMessage.put(peer, currentTime);
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
                messageConsumer.accept(messageBuilder.build());
            }
        }
    }

    private class Cleaner implements Runnable {

        @Override
        public void run() {
            rwLock.writeLock().lock();
            try {
                long lruEventTime = lastSentPEXMessage.values().stream()
                        .reduce(Long.MAX_VALUE, (a, b) -> (a < b) ? a : b);;

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Prior to cleaning events. LRU event time: {}, peer events: {}", lruEventTime, peerEvents);
                }

                PeerEvent event;
                for (Queue<PeerEvent> events : peerEvents.values()) {
                    while ((event = events.peek()) != null && event.getInstant() <= lruEventTime) {
                        events.poll();
                    }
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("After cleaning events. Peer events: {}", peerEvents);
                }

            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }
}
