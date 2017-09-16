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

import bt.net.Peer;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracker response.
 *
 * @since 1.0
 */
public class TrackerResponse {

    /**
     * @return Empty success response.
     * @since 1.0
     */
    public static TrackerResponse ok() {
        return new TrackerResponse();
    }

    /**
     * @return Failure response with the provided message.
     * @since 1.0
     */
    public static TrackerResponse failure(String errorMessage) {
        return new TrackerResponse(errorMessage);
    }

    /**
     * @return Exceptional response with the provided exception.
     *         Usually means that interaction with the tracker failed due to a I/O error,
     *         or a malformed response was received from the tracker.
     * @since 1.0
     */
    public static TrackerResponse exceptional(Throwable error) {
        return new TrackerResponse(error);
    }

    private final boolean success;
    private final Optional<Throwable> error;

    private String errorMessage;
    private String warningMessage;
    private int interval;
    private int minInterval;
    private Optional<byte[]> trackerId;
    private int seederCount;
    private int leecherCount;

    private Iterable<Peer> peers;

    /**
     * Create an empty success response.
     *
     * @since 1.0
     */
    protected TrackerResponse() {
        this(true, null);
    }

    /**
     * Create a failure response with the provided message.
     *
     * @since 1.0
     */
    protected TrackerResponse(String errorMessage) {
        this(false, null);
        this.errorMessage = errorMessage;
    }

    /**
     * Create an exceptional response with the provided exception.
     * Usually means that interaction with the tracker failed due to a I/O error,
     * or a malformed response was received from the tracker.
     *
     * @since 1.0
     */
    protected TrackerResponse(Throwable error) {
        this(false, Objects.requireNonNull(error));
    }

    private TrackerResponse(boolean success, Throwable error) {
        this.success = success;
        this.error = Optional.ofNullable(error);
        this.trackerId = Optional.empty();
        this.peers = Collections.emptyList();
    }

    /**
     * @return true if the tracker response has been received
     *         and it does not contain a failure message.
     * @since 1.0
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return Failure message, received from the tracker.
     * @since 1.0
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @return Warning message, received from the tracker.
     *         Note that a success response may also contain
     *         a warning message as additional info.
     * @since 1.0
     */
    public String getWarningMessage() {
        return warningMessage;
    }

    /**
     * @see #getWarningMessage()
     * @since 1.0
     */
    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    /**
     * @return Exception that happened during interaction with the tracker.
     * @since 1.0
     */
    public Optional<Throwable> getError() {
        return error;
    }

    /**
     * @return Preferred interval for querying peers from this tracker.
     * @since 1.0
     */
    public int getInterval() {
        return interval;
    }

    /**
     * @see #getInterval()
     * @since 1.0
     */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * @return Absolutely minimal interval for querying peers from this tracker.
     * @since 1.0
     */
    public int getMinInterval() {
        return minInterval;
    }

    /**
     * @see #getMinInterval()
     * @since 1.0
     */
    public void setMinInterval(int minInterval) {
        this.minInterval = minInterval;
    }

    /**
     * @return Binary tracker ID.
     * @since 1.0
     */
    public Optional<byte[]> getTrackerId() {
        return trackerId;
    }

    /**
     * @see #getTrackerId()
     * @since 1.0
     */
    public void setTrackerId(byte[] trackerId) {
        this.trackerId = Optional.of(trackerId);
    }

    /**
     * @return Number of seeders for the requested torrent.
     * @since 1.0
     */
    public int getSeederCount() {
        return seederCount;
    }

    /**
     * @see #getSeederCount()
     * @since 1.0
     */
    public void setSeederCount(int seederCount) {
        this.seederCount = seederCount;
    }

    /**
     * @return Number of leechers for the requested torrent.
     * @since 1.0
     */
    public int getLeecherCount() {
        return leecherCount;
    }

    /**
     * @see #getLeecherCount()
     * @since 1.0
     */
    public void setLeecherCount(int leecherCount) {
        this.leecherCount = leecherCount;
    }

    /**
     * @return Collection of peers, that are active for the requested torrent.
     * @since 1.0
     */
    public Iterable<Peer> getPeers() {
        return peers;
    }

    /**
     * @see #getPeers()
     * @since 1.0
     */
    public void setPeers(Iterable<Peer> peers) {
        this.peers = peers;
    }
}
