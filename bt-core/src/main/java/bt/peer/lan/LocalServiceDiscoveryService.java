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
import bt.net.PeerConnectionAcceptor;
import bt.net.SocketChannelConnectionAcceptor;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.LifecycleBinding;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Performs periodic announces of currently active torrents to specific multicast groups.
 * See BEP-14 for more details.
 *
 * @since 1.6
 */
public class LocalServiceDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalServiceDiscoveryService.class);

    private final Cookie cookie;
    private final Set<SocketChannelConnectionAcceptor> socketAcceptors;
    private final Config config;
    private final ScheduledExecutorService executor;

    private final LinkedHashSet<TorrentId> announceQueue;
    private final BlockingQueue<Event> events;

    private volatile Collection<LocalServiceDiscoveryAnnouncer> announcers;
    private volatile boolean shutdown;

    @Inject
    public LocalServiceDiscoveryService(Cookie cookie,
                                        Set<PeerConnectionAcceptor> connectionAcceptors,
                                        EventSource eventSource,
                                        IRuntimeLifecycleBinder lifecycleBinder,
                                        Config config) {
        this.cookie = cookie;
        this.socketAcceptors = connectionAcceptors.stream()
                .filter(a -> a instanceof SocketChannelConnectionAcceptor)
                .map(a -> (SocketChannelConnectionAcceptor)a)
                .collect(Collectors.toSet());

        this.config = config;
        this.announceQueue = new LinkedHashSet<>();
        this.events = new LinkedBlockingQueue<>();

        eventSource.onTorrentStarted(this::onTorrentStarted);
        eventSource.onTorrentStopped(this::onTorrentStopped);

        this.executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "lsd-announcer"));
        long intervalMillis = config.getLocalServiceDiscoveryAnnounceInterval().toMillis();
        Runnable r = () -> executor.scheduleWithFixedDelay(() -> {
            try {
                announce();
            } catch (Exception e) {
                LOGGER.error("Failed to announce", e);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        lifecycleBinder.onStartup(LifecycleBinding.bind(r).description("Start Local Service Discovery announcer").async().build());
        lifecycleBinder.onShutdown(executor::shutdownNow);
        lifecycleBinder.onShutdown(this::shutdown);
    }

    public void announce() {
        if (announceQueue.isEmpty() && events.isEmpty()) {
            return;
        }

        Collection<TorrentId> activeIds = collectCurrentlyActiveTorrents(events);
        Collection<TorrentId> idsToAnnounce = collectNextTorrents(activeIds);

        if (idsToAnnounce.size() > 0) {
            announce(idsToAnnounce);
        }
    }

    private Collection<TorrentId> collectCurrentlyActiveTorrents(BlockingQueue<Event> events) {
        int k = events.size(); // decide on the number of events to process upfront

        Set<TorrentId> ids = new HashSet<>(k * 2);
        Event event;
        while (--k >= 0 && (event = events.poll()) != null) {
            if (event instanceof TorrentStartedEvent) {
                ids.add(((TorrentStartedEvent) event).getTorrentId());
            } else if (event instanceof TorrentStoppedEvent) {
                ids.remove(((TorrentStoppedEvent) event).getTorrentId());
            } else {
                LOGGER.warn("Unexpected event type: " + event.getClass().getName() + ". Skipping...");
            }
        }
        return ids;
    }

    /**
     * Collect next few IDs to announce and additionally remove all inactive IDs from the queue.
     */
    private Collection<TorrentId> collectNextTorrents(Collection<TorrentId> activeIds) {
        int k = config.getLocalServiceDiscoveryMaxTorrentsPerAnnounce();
        List<TorrentId> ids = new ArrayList<>(k * 2);
        Iterator<TorrentId> iter = announceQueue.iterator();
        while (iter.hasNext()) {
            TorrentId id = iter.next();
            if (activeIds.contains(id)) {
                if (ids.size() < k) {
                    iter.remove(); // temporary remove from the queue
                    ids.add(id);
                    announceQueue.add(id); // add to the end of the queue
                }
            } else {
                // remove inactive
                iter.remove();
            }
        }
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
            // todo: filter ip4 and ip6 groups based on acceptors
            Collection<Integer> localPorts = socketAcceptors.stream()
                    .map(a -> a.getLocalAddress().getPort())
                    .collect(Collectors.toSet());

            announcers = config.getLocalServiceDiscoveryAnnounceGroups().stream()
                    .map(group -> new LocalServiceDiscoveryAnnouncer(group, cookie, localPorts))
                    .collect(Collectors.toList());

            if (shutdown) {
                // in case shutdown was called just before assigning announcers
                shutdownAnnouncers();
            }
        }
        return announcers;
    }

    private void onTorrentStarted(TorrentStartedEvent event) {
        events.add(event);
    }

    private void onTorrentStopped(TorrentStoppedEvent event) {
        events.add(event);
    }

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
