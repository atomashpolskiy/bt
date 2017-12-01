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
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class LocalServiceDiscoveryService implements ILocalServiceDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalServiceDiscoveryService.class);

    private final IRuntimeLifecycleBinder lifecycleBinder;
    private final LocalServiceDiscoveryConfig config;

    private final LinkedHashSet<TorrentId> announceQueue;
    private final BlockingQueue<Event> events;

    private final Collection<LocalServiceDiscoveryAnnouncer> announcers;

    private final AtomicBoolean scheduled; // true, if periodic announce has been scheduled

    @Inject
    public LocalServiceDiscoveryService(Cookie cookie,
                                        ILocalServiceDiscoveryInfo info,
                                        Collection<AnnounceGroupChannel> groupChannels,
                                        EventSource eventSource,
                                        IRuntimeLifecycleBinder lifecycleBinder,
                                        LocalServiceDiscoveryConfig config) {

        this.lifecycleBinder = lifecycleBinder;
        this.config = config;
        this.announceQueue = new LinkedHashSet<>();
        this.events = new LinkedBlockingQueue<>();

        this.announcers = createAnnouncers(groupChannels, cookie, info.getLocalPorts());

        this.scheduled = new AtomicBoolean(false);

        // do not enable LSD if there are no groups to announce to
        if (groupChannels.size() > 0) {
            eventSource.onTorrentStarted(this::onTorrentStarted);
            eventSource.onTorrentStopped(this::onTorrentStopped);
        }
    }

    private Collection<LocalServiceDiscoveryAnnouncer> createAnnouncers(
            Collection<AnnounceGroupChannel> groupChannels,
            Cookie cookie,
            Set<Integer> localPorts) {

        return groupChannels.stream()
                .map(channel -> new LocalServiceDiscoveryAnnouncer(channel, cookie, localPorts, config))
                .collect(Collectors.toList());
    }

    private void schedulePeriodicAnnounce() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "lsd-announcer"));
        long intervalMillis = config.getLocalServiceDiscoveryAnnounceInterval().toMillis();
        executor.scheduleWithFixedDelay(this::announce, 0, intervalMillis, TimeUnit.MILLISECONDS);
        lifecycleBinder.onShutdown(executor::shutdownNow);
    }

    // TODO: using synchronized for now, because this method is available from the public API
    // (however, it's unlikely to be called from anywhere other than tests)
    @Override
    public synchronized void announce() {
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
                if (ids.size() < k) {
                    ids.add(id);
                }
            }
        });
        return ids;
    }

    private void announce(Collection<TorrentId> ids) {
        // TODO: announce in parallel?
        announcers.forEach(a -> {
            try {
                a.announce(ids);
            } catch (IOException e) {
                LOGGER.error("Failed to announce to group: " + a.getGroup().getAddress(), e);
            }
        });
    }

    private void onTorrentStarted(TorrentStartedEvent event) {
        events.add(event);
        if (scheduled.compareAndSet(false, true)) {
            // schedule periodic announce immediately after the first torrent has been started
            // TODO: immediately announce each time a torrent is started (but no more than 1 announce per minute)
            schedulePeriodicAnnounce();
        }
    }

    private void onTorrentStopped(TorrentStoppedEvent event) {
        events.add(event);
    }
}
