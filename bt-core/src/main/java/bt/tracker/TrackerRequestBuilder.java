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

import bt.BtException;
import bt.metainfo.TorrentId;

import java.util.Objects;

/**
 * Base class for tracker request builders.
 *
 * @since 1.0
 */
public abstract class TrackerRequestBuilder {

    private final TorrentId torrentId;
    private Integer numWant;

    /**
     * Create a tracker request builder for a given torrent ID
     *
     * @since 1.0
     */
    protected TrackerRequestBuilder(TorrentId torrentId) {
        this.torrentId = Objects.requireNonNull(torrentId);
    }

    /**
     * Announce to tracker, that the client is starting a torrent session.
     *
     * @return Tracker response
     * @since 1.0
     */
    public abstract TrackerResponse start();

    /**
     * Announce to tracker, that the client is stopping a torrent session.
     *
     * @return Tracker response
     * @since 1.0
     */
    public abstract TrackerResponse stop();

    /**
     * Announce to tracker, that the client has completed downloading the torrent.
     *
     * @return Tracker response
     * @since 1.0
     */
    public abstract TrackerResponse complete();

    /**
     * Query tracker for active peers.
     *
     * @return Tracker response
     * @since 1.0
     */
    public abstract TrackerResponse query();

    /**
     * Set the number of peers to request in this call to the tracker. Set to null to use the default value
     *
     * @param numWant the number of peers to request from the tracker
     * @return Builder
     * @since 1.10
     */
    public TrackerRequestBuilder numWant(Integer numWant) {
        if (numWant != null && numWant < 0) {
            throw new BtException("Invalid numWant value: " + numWant);
        }
        this.numWant = numWant;
        return this;
    }

    /**
     * @since 1.0
     */
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * Get the number of peers to request from the tracker, or null if the default value should be used
     *
     * @return the number of peers to request from the tracker
     * @since 1.10
     */
    public Integer getNumWant() {
        return numWant;
    }
}
