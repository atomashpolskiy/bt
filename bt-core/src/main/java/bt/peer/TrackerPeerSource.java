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

package bt.peer;

import bt.BtException;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.tracker.Tracker;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

class TrackerPeerSource extends ScheduledPeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerPeerSource.class);
    private static final Duration DEFAULT_WAIT_ON_FAILURE = Duration.ofMinutes(1);
    private static final long NEVER_ANNOUNCED = 0;

    private final Tracker tracker;
    private final TorrentId torrentId;
    private Duration trackerQueryInterval;
    private final boolean useTrackerAnnounceInterval;

    private final AtomicLong lastRefreshed = new AtomicLong(NEVER_ANNOUNCED);
    private boolean firstRequest = true;

    TrackerPeerSource(ExecutorService executor, Tracker tracker, TorrentId torrentId,
                      Duration defaultTrackerQueryInterval, Duration trackerTimeout) {
        super(executor, trackerTimeout);
        this.tracker = tracker;
        this.torrentId = torrentId;
        this.useTrackerAnnounceInterval = defaultTrackerQueryInterval == null;
        this.trackerQueryInterval = defaultTrackerQueryInterval == null ? DEFAULT_WAIT_ON_FAILURE : defaultTrackerQueryInterval;
    }

    @Override
    protected void collectPeers(Consumer<Peer> peerConsumer) {
        final long reqStartTime = System.currentTimeMillis();
        final long lastRefreshTime = lastRefreshed.get();

        if ((reqStartTime - lastRefreshTime) >= trackerQueryInterval.toMillis()) {

            // use atomic CaS - avoid double announce.
            if (lastRefreshed.compareAndSet(lastRefreshTime, reqStartTime)) {
                TrackerResponse response;
                try {
                    if (firstRequest) {
                        response = tracker.request(torrentId).start();
                    } else {
                        response = tracker.request(torrentId).query();
                    }
                } finally {
                    // set the last refreshed time to the current time
                    lastRefreshed.compareAndSet(reqStartTime, System.currentTimeMillis());
                }

                if (response.isSuccess()) {
                    firstRequest = false;
                    response.getPeers().forEach(peerConsumer::accept);

                    // if interval was not specified in config, use what the tracker provides
                    if (useTrackerAnnounceInterval) {
                        if (response.getInterval() <= 0) {
                            // this interval is a tracker bug. Use default of 5 minutes.
                            trackerQueryInterval = Duration.ofMinutes(5);
                        } else {
                            trackerQueryInterval = Duration.ofSeconds(response.getInterval());
                        }
                    }

                    // ensure that min interval is respected
                    if (response.getMinInterval() > trackerQueryInterval.getSeconds()) {
                        LOGGER.info("Tracker min interval {} is less than configured query interval {}. Using Tracker's min interval.",
                                response.getMinInterval(), trackerQueryInterval);
                        trackerQueryInterval = Duration.ofSeconds(response.getMinInterval());
                    }
                } else {
                    // if the response failed, wait 1 minute before trying again, unless query interval was manually
                    // configured, in which case we use it on failure too.
                    if (useTrackerAnnounceInterval)
                        trackerQueryInterval = DEFAULT_WAIT_ON_FAILURE;

                    if (response.getError().isPresent()) {
                        throw new BtException("Failed to get peers for torrent", response.getError().get());
                    } else {
                        LOGGER.error("Failed to get peers for torrent -- " +
                                "unexpected error during interaction with the tracker; message: " + response.getErrorMessage());
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return "TrackerPeerSource {" + tracker + "}";
    }
}
