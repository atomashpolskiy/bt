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

import bt.net.Peer;
import bt.tracker.TrackerResponse;
import yourip.Main;

public class MockTrackerResponse extends TrackerResponse {
    private static final MockTrackerResponse instance = new MockTrackerResponse();

    public static MockTrackerResponse instance() {
        return instance;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public Iterable<Peer> getPeers() {
        return Main.peers();
    }
}
