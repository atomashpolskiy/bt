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

package yourip.mock;

import bt.metainfo.TorrentId;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;

public class MockTracker implements Tracker {
    private static final String url = MockTrackerFactory.schema() + "://mock";

    public static String url() {
        return url;
    }

    @Override
    public TrackerRequestBuilder request(TorrentId torrentId) {
        return new TrackerRequestBuilder(torrentId) {
            @Override
            public TrackerResponse start() {
                return MockTrackerResponse.instance();
            }

            @Override
            public TrackerResponse stop() {
                return MockTrackerResponse.instance();
            }

            @Override
            public TrackerResponse complete() {
                return MockTrackerResponse.instance();
            }

            @Override
            public TrackerResponse query() {
                return MockTrackerResponse.instance();
            }
        };
    }
}
