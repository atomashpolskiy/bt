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

    private TorrentId torrentId;

    private long uploaded;
    private long downloaded;
    private long left;

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
     * Optionally set the amount of data uploaded during the current session.
     *
     * @param uploaded Amount of data uploaded since the last announce, in bytes.
     * @return Builder
     * @since 1.0
     */
    public TrackerRequestBuilder uploaded(long uploaded) {
        if (uploaded < 0) {
            throw new BtException("Invalid uploaded value: " + uploaded);
        }
        this.uploaded = uploaded;
        return this;
    }

    /**
     * Optionally set the amount of data downloaded during the current session.
     *
     * @param downloaded Amount of data downloaded since the last announce, in bytes.
     * @return Builder
     * @since 1.0
     */
    public TrackerRequestBuilder downloaded(long downloaded) {
        if (downloaded < 0) {
            throw new BtException("Invalid downloaded value: " + downloaded);
        }
        this.downloaded = downloaded;
        return this;
    }

    /**
     * Optionally set the amount of data left for the client to download.
     *
     * @param left Amount of data that is left for the client to complete the torrent download, in bytes.
     * @return Builder
     * @since 1.0
     */
    public TrackerRequestBuilder left(long left) {
        if (left < 0) {
            throw new BtException("Invalid left value: " + left);
        }
        this.left = left;
        return this;
    }

    /**
     * @since 1.0
     */
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.0
     */
    public long getUploaded() {
        return uploaded;
    }

    /**
     * @since 1.0
     */
    public long getDownloaded() {
        return downloaded;
    }

    /**
     * @since 1.0
     */
    public long getLeft() {
        return left;
    }
}
