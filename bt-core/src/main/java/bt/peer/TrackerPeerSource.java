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

    private static final long NEVER_ANNOUNCED = Long.MIN_VALUE;
    private final Tracker tracker;
    private final TorrentId torrentId;
    private Duration trackerQueryInterval;

    private final AtomicLong lastRefreshed = new AtomicLong(NEVER_ANNOUNCED);

    TrackerPeerSource(ExecutorService executor, Tracker tracker, TorrentId torrentId, Duration trackerQueryInterval) {
        super(executor);
        this.tracker = tracker;
        this.torrentId = torrentId;
        this.trackerQueryInterval = trackerQueryInterval;
    }

    @Override
    protected void collectPeers(Consumer<Peer> peerConsumer) {
        final long now = System.currentTimeMillis();
        final long lastRefreshTime = lastRefreshed.get();

        if (lastRefreshTime == NEVER_ANNOUNCED || (now - lastRefreshTime) >= trackerQueryInterval.toMillis()) {

            // use atomic CaS - avoid double announce.
            if (lastRefreshed.compareAndSet(lastRefreshTime, now)) {
                TrackerResponse response;
                try {
                    // TODO: report stats
                    if (lastRefreshTime == NEVER_ANNOUNCED)
                        response = tracker.request(torrentId).start();
                    else
                        response = tracker.request(torrentId).query();

                    // ensure that min interval is respected
                    if (response.isSuccess() && response.getMinInterval() > trackerQueryInterval.getSeconds()) {
                        trackerQueryInterval = Duration.ofSeconds(response.getMinInterval());
                    }
                } finally {
                    lastRefreshed.set(System.currentTimeMillis());
                }

                if (response.isSuccess()) {
                    response.getPeers().forEach(peerConsumer::accept);
                } else {
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
