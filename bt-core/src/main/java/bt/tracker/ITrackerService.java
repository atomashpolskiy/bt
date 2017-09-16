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

package bt.tracker;

/**
 * This service acts a factory of trackers.
 *
 * @since 1.0
 */
public interface ITrackerService {

    /**
     * Check if the protocol specified in the tracker's URL is supported.
     *
     * @param trackerUrl Tracker URL
     * @return true if the protocol is supported
     * @since 1.1
     */
    boolean isSupportedProtocol(String trackerUrl);

    /**
     * Get a single tracker by its' URL
     *
     * @return Single tracker
     * @throws bt.BtException if the protocol specified in the tracker's URL is not supported
     * @since 1.0
     */
    Tracker getTracker(String trackerUrl);

    /**
     * Get a tracker by its' announce key
     *
     * @return Either a single-tracker or a multi-tracker,
     *         depending of the type of the announce key
     * @since 1.0
     */
    Tracker getTracker(AnnounceKey announceKey);
}
