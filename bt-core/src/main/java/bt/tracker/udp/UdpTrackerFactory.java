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

package bt.tracker.udp;

import bt.runtime.Config;
import bt.service.IdentityService;
import bt.torrent.TorrentRegistry;
import bt.tracker.Tracker;
import bt.tracker.TrackerFactory;
import com.google.inject.Inject;

/**
 * Creates UDP tracker clients.
 *
 * @since 1.0
 */
public class UdpTrackerFactory implements TrackerFactory {

    private final IdentityService idService;
    private final TorrentRegistry torrentRegistry;
    private final Config config;

    @Inject
    public UdpTrackerFactory(IdentityService idService, TorrentRegistry torrentRegistry, Config config) {
        this.idService = idService;
        this.torrentRegistry = torrentRegistry;
        this.config = config;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new UdpTracker(idService, torrentRegistry, config.getAcceptorAddress(),
                config.getAcceptorPort(), config.getNumberOfPeersToRequestFromTracker(), trackerUrl, config.getTrackerTimeout());
    }
}
