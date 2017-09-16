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

import java.util.Collections;
import java.util.List;

/**
 * This class encapsulates information about trackers,
 * that can be used for getting a particular torrent.
 *
 * @since 1.0
 */
public class AnnounceKey {

    private final String trackerUrl;
    private final List<List<String>> trackerUrls;

    /**
     * Create a single-tracker announce key
     *
     * @since 1.0
     */
    public AnnounceKey(String trackerUrl) {
        this.trackerUrl = trackerUrl;
        this.trackerUrls = null;
    }

    /**
     * Create a multi-tracker announce key
     * See BEP-12: Multitracker Metadata Extension for more details
     *
     * @param trackerUrls List of tiers of trackers (first list contains primary trackers,
     *                    subsequent lists contain backup trackers)
     * @since 1.0
     */
    public AnnounceKey(List<List<String>> trackerUrls) {
        this.trackerUrl = null;
        this.trackerUrls = Collections.unmodifiableList(trackerUrls);
    }

    /**
     * @return true if this announce key supports multi-trackers
     * @since 1.0
     */
    public boolean isMultiKey() {
        return trackerUrls != null;
    }

    /**
     * @return Tracker URL if {@link #isMultiKey()} is false, null otherwise
     * @since 1.0
     */
    public String getTrackerUrl() {
        return trackerUrl;
    }

    /**
     * @return List of Tracker tiers if {@link #isMultiKey()} is true, null otherwise
     * @since 1.0
     */
    public List<List<String>> getTrackerUrls() {
        return trackerUrls;
    }

    @Override
    public String toString() {
        if (isMultiKey()) {
            return trackerUrls.toString();
        } else {
            return trackerUrl;
        }
    }

    @Override
    public int hashCode() {
        return isMultiKey()? trackerUrls.hashCode() : trackerUrl.hashCode();
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AnnounceKey that = (AnnounceKey) o;

        return isMultiKey()? trackerUrls.equals(that.trackerUrls) : trackerUrl.equals(that.trackerUrl);
    }
}
