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

package bt.peer.lan;

import bt.event.Event;
import bt.event.EventSource;
import bt.event.TorrentStartedEvent;
import bt.event.TorrentStoppedEvent;
import bt.metainfo.TorrentId;
import bt.net.SocketChannelConnectionAcceptor;
import bt.runtime.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class LocalServiceDiscoveryService implements ILocalServiceDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalServiceDiscoveryService.class);

    private static final int IP4_BYTES = 4;
    private static final int IP6_BYTES = 16;

    private final Cookie cookie;
    private final Set<SocketChannelConnectionAcceptor> socketAcceptors;
    private final Config config;

    private final LinkedHashSet<TorrentId> announceQueue;
    private final BlockingQueue<Event> events;

    private final Selector selector;
    private volatile Collection<LocalServiceDiscoveryAnnouncer> announcers;
    private volatile boolean shutdown;

    public LocalServiceDiscoveryService(Cookie cookie,
                                        Set<SocketChannelConnectionAcceptor> socketAcceptors,
                                        Selector selector,
                                        EventSource eventSource,
                                        Config config) {
        this.cookie = cookie;
        this.socketAcceptors = socketAcceptors;
        this.selector = selector;
        this.config = config;
        this.announceQueue = new LinkedHashSet<>();
        this.events = new LinkedBlockingQueue<>();

        eventSource.onTorrentStarted(this::onTorrentStarted);
        eventSource.onTorrentStopped(this::onTorrentStopped);
    }

    @Override
    public void announce() {
        if (announceQueue.isEmpty() && events.isEmpty()) {
            return;
        }

        try {
            Map<TorrentId, StatusChange> statusChanges = foldStartStopEvents(events);
            Collection<TorrentId> idsToAnnounce = collectNextTorrents(statusChanges);

            if (idsToAnnounce.size() > 0) {
                announce(idsToAnnounce);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to announce", e);
        }
    }

    private enum StatusChange {
        STARTED, STOPPED
    }

    /**
     * Folds started/stopped events into a map of status changes
     */
    private Map<TorrentId, StatusChange> foldStartStopEvents(BlockingQueue<Event> events) {
        int k = events.size(); // decide on the number of events to process upfront

        Map<TorrentId, StatusChange> statusChanges = new HashMap<>(k * 2);
        Event event;
        while (--k >= 0 && (event = events.poll()) != null) {
            if (event instanceof TorrentStartedEvent) {
                statusChanges.put(((TorrentStartedEvent) event).getTorrentId(), StatusChange.STARTED);
            } else if (event instanceof TorrentStoppedEvent) {
                statusChanges.put(((TorrentStoppedEvent) event).getTorrentId(), StatusChange.STOPPED);
            } else {
                LOGGER.warn("Unexpected event type: " + event.getClass().getName() + ". Skipping...");
            }
        }
        return statusChanges;
    }

    /**
     * Collect next few IDs to announce and additionally remove all inactive IDs from the queue.
     */
    private Collection<TorrentId> collectNextTorrents(Map<TorrentId, StatusChange> statusChanges) {
        int k = config.getLocalServiceDiscoveryMaxTorrentsPerAnnounce();
        List<TorrentId> ids = new ArrayList<>(k * 2);
        Iterator<TorrentId> iter = announceQueue.iterator();
        while (iter.hasNext()) {
            TorrentId id = iter.next();
            StatusChange statusChange = statusChanges.get(id);
            if (statusChange == null) {
                if (ids.size() < k) {
                    iter.remove(); // temporary remove from the queue
                    ids.add(id);
                    announceQueue.add(id); // add to the end of the queue
                }
            } else if (statusChange == StatusChange.STOPPED) {
                // remove inactive
                iter.remove();
            }
            // last case is that the torrent has been stopped and started in between the announces,
            // which means that we should leave it in the announce queue
        }
        // add all new started torrents to the announce queue
        statusChanges.forEach((id, statusChange) -> {
            if (statusChange == StatusChange.STARTED) {
                announceQueue.add(id);
            }
        });
        return ids;
    }

    private void announce(Collection<TorrentId> ids) {
        // TODO: announce in parallel?
        getAnnouncers().forEach(a -> {
            try {
                a.announce(ids);
            } catch (IOException e) {
                LOGGER.error("Failed to announce to group: " + a.getGroup().getAddress(), e);
            }
        });
    }

    private Collection<LocalServiceDiscoveryAnnouncer> getAnnouncers() {
        if (announcers == null) {
            Set<Integer> localPorts = new HashSet<>();
            boolean acceptIP4 = false;
            boolean acceptIP6 = false;

            for (SocketChannelConnectionAcceptor acceptor : socketAcceptors) {
                InetSocketAddress address = acceptor.getLocalAddress();
                if (isIP4(address)) {
                    acceptIP4 = true;
                    localPorts.add(acceptor.getLocalAddress().getPort());
                } else if (isIP6(address)) {
                    acceptIP6 = true;
                    localPorts.add(acceptor.getLocalAddress().getPort());
                } else {
                    LOGGER.warn("Unexpected address (not IP4/IP6): " + address);
                }
            }

            announcers = getAnnouncers(config.getLocalServiceDiscoveryAnnounceGroups(), acceptIP4, acceptIP6, localPorts);

            if (shutdown) {
                // in case shutdown was called just before assigning announcers
                shutdownAnnouncers();
            }
        }
        return announcers;
    }

    private Collection<LocalServiceDiscoveryAnnouncer> getAnnouncers(
            Collection<AnnounceGroup> groups, boolean acceptIP4, boolean acceptIP6, Collection<Integer> localPorts) {
        return groups.stream()
                .filter(group -> (isIP4(group.getAddress()) && acceptIP4) || (isIP6(group.getAddress()) && acceptIP6))
                .map(group -> new LocalServiceDiscoveryAnnouncer(selector, group, cookie, localPorts))
                .collect(Collectors.toList());
    }

    private static boolean isIP4(InetSocketAddress address) {
        return address.getAddress().getAddress().length == IP4_BYTES;
    }

    private static boolean isIP6(InetSocketAddress address) {
        return address.getAddress().getAddress().length == IP6_BYTES;
    }

    private void onTorrentStarted(TorrentStartedEvent event) {
        events.add(event);
    }

    private void onTorrentStopped(TorrentStoppedEvent event) {
        events.add(event);
    }

    @Override
    public void shutdown() {
        shutdown = true;
        shutdownAnnouncers();
    }

    private void shutdownAnnouncers() {
        if (announcers != null) {
            announcers.forEach(LocalServiceDiscoveryAnnouncer::shutdown);
        }
    }
}
