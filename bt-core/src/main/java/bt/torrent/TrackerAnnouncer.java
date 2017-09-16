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

package bt.torrent;

import bt.metainfo.Torrent;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @since 1.3
 */
public class TrackerAnnouncer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerAnnouncer.class);

    private enum Event {
        start, stop, complete
    }

    private final Optional<Tracker> trackerOptional;
    private final Torrent torrent;
    private final TorrentSessionState sessionState;

    public TrackerAnnouncer(ITrackerService trackerService,
                            Torrent torrent,
                            AnnounceKey announceKey,
                            TorrentSessionState sessionState) {
        this.trackerOptional = Optional.ofNullable(createTracker(trackerService, announceKey));
        this.torrent = torrent;
        this.sessionState = sessionState;
    }

    private Tracker createTracker(ITrackerService trackerService, AnnounceKey announceKey) {
        try {
            String trackerUrl = getTrackerUrl(announceKey);
            if (trackerService.isSupportedProtocol(trackerUrl)) {
                return trackerService.getTracker(announceKey);
            } else {
                LOGGER.warn("Tracker URL protocol is not supported: " + trackerUrl);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create tracker for announce key: " + announceKey);
        }
        return null;
    }

    private String getTrackerUrl(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            return announceKey.getTrackerUrls().get(0).get(0);
        } else {
            return announceKey.getTrackerUrl();
        }
    }

    public void start() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.start, tracker, prepareAnnounce(tracker).start());
            } catch (Exception e) {
                logTrackerError(Event.start, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void stop() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.stop, tracker, prepareAnnounce(tracker).stop());
            } catch (Exception e) {
                logTrackerError(Event.stop, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void complete() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.complete, tracker, prepareAnnounce(tracker).complete());
            } catch (Exception e) {
                logTrackerError(Event.complete, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    private TrackerRequestBuilder prepareAnnounce(Tracker tracker) {
        return tracker.request(torrent.getTorrentId())
                .downloaded(sessionState.getDownloaded())
                .uploaded(sessionState.getUploaded())
                .left(sessionState.getPiecesRemaining() * torrent.getChunkSize());
    }

    private void processResponse(Event event, Tracker tracker, TrackerResponse response) {
        if (!response.isSuccess()) {
            logTrackerError(event, tracker, response.getError(), Optional.ofNullable(response.getErrorMessage()));
        }
    }

    private void logTrackerError(Event event, Tracker tracker, Optional<Throwable> e, Optional<String> message) {
        String log = String.format("Failed to announce '%s' event due to unexpected error " +
                "during interaction with the tracker: %s", event.name(), tracker);
        if (message.isPresent()) {
            log += "; message: " + message;
        }

        if (e.isPresent()) {
            LOGGER.error(log, e.get());
        } else {
            LOGGER.warn(log);
        }
    }
}
