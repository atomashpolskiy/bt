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

package bt.torrent.messaging;

import bt.protocol.Cancel;
import bt.protocol.Request;
import bt.torrent.data.BlockWrite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Contains basic information about a connection's state.
 *
 * @since 1.0
 */
public class ConnectionState {

    private volatile boolean interested;
    private volatile boolean peerInterested;
    private volatile boolean choking;
    private volatile boolean peerChoking;

    private volatile long downloaded;
    private volatile long uploaded;

    private Optional<Boolean> shouldChoke;
    private long lastChoked;

    private Set<Object> cancelledPeerRequests;
    private Set<Object> pendingRequests;
    private Map<Object, CompletableFuture<BlockWrite>> pendingWrites;

    private Queue<Request> requestQueue;
    private boolean initializedRequestQueue;
    private Optional<Assignment> assignment;

    ConnectionState() {
        this.choking = true;
        this.peerChoking = true;
        this.shouldChoke = Optional.empty();
        this.cancelledPeerRequests = new HashSet<>();
        this.pendingRequests = new HashSet<>();
        this.pendingWrites = new HashMap<>();

        this.requestQueue = new LinkedBlockingQueue<>();

        this.assignment = Optional.empty();
    }

    /**
     * @return true if the local client is interested in (some of the) pieces that remote peer has
     * @since 1.0
     */
    public boolean isInterested() {
        return interested;
    }

    /**
     * @see #isInterested()
     * @since 1.0
     */
    public void setInterested(boolean interested) {
        this.interested = interested;
    }

    /**
     * @return true if remote peer is interested in (some of the) pieces that the local client has
     * @since 1.0
     */
    public boolean isPeerInterested() {
        return peerInterested;
    }

    /**
     * @see #isPeerInterested()
     * @since 1.0
     */
    public void setPeerInterested(boolean peerInterested) {
        this.peerInterested = peerInterested;
    }

    /**
     * @return true if the local client is choking the connection
     * @since 1.0
     */
    public boolean isChoking() {
        return choking;
    }

    /**
     * @see #isChoking()
     */
    void setChoking(boolean choking) {
        this.choking = choking;
        this.shouldChoke = Optional.empty();
    }

    /**
     * @return Optional boolean, if choking/unchoking has been proposed, null otherwise
     * @since 1.0
     */
    public Optional<Boolean> getShouldChoke() {
        return shouldChoke;
    }

    /**
     * Propose choking/unchoking.
     *
     * @see Choker
     * @since 1.0
     */
    public void setShouldChoke(boolean shouldChoke) {
        this.shouldChoke = Optional.of(shouldChoke);
    }

    /**
     * @return Last time connection was choked, 0 if it hasn't been choked yet.
     *         Note that connections are initially choked when created.
     * @since 1.0
     */
    public long getLastChoked() {
        return lastChoked;
    }

    /**
     * @see #getLastChoked()
     * @since 1.0
     */
    void setLastChoked(long lastChoked) {
        this.lastChoked = lastChoked;
    }

    /**
     * @return true if remote peer is choking the connection
     * @since 1.0
     */
    public boolean isPeerChoking() {
        return peerChoking;
    }

    /**
     * @see #isPeerChoking()
     * @since 1.0
     */
    public void setPeerChoking(boolean peerChoking) {
        this.peerChoking = peerChoking;
    }

    /**
     * @return Amount of data downloaded from remote peer via this connection
     * @since 1.0
     */
    public long getDownloaded() {
        return downloaded;
    }

    /**
     * @see #getDownloaded()
     * @since 1.0
     */
    public void incrementDownloaded(long downloaded) {
        this.downloaded += downloaded;
    }

    /**
     * @return Amount of data uploaded to remote peer via this connection
     * @since 1.0
     */
    public long getUploaded() {
        return uploaded;
    }

    /**
     * @see #getUploaded()
     * @since 1.0
     */
    public void incrementUploaded(long uploaded) {
        this.uploaded += uploaded;
    }

    /**
     * Get keys of block requests, that have been cancelled by remote peer.
     *
     * @see Mapper#buildKey(int, int, int)
     * @return Set of block request keys
     * @since 1.0
     */
    public Set<Object> getCancelledPeerRequests() {
        return cancelledPeerRequests;
    }

    /**
     * Signal that remote peer has cancelled a previously issued block request.
     *
     * @since 1.0
     */
    public void onCancel(Cancel cancel) {
        cancelledPeerRequests.add(Mapper.mapper().buildKey(
                cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength()));
    }

    /**
     * Get keys of block requests, that have been sent to the remote peer.
     *
     * @see Mapper#buildKey(int, int, int)
     * @return Set of block request keys
     * @since 1.0
     */
    public Set<Object> getPendingRequests() {
        return pendingRequests;
    }

    /**
     * Get pending block writes, mapped by keys of corresponding requests.
     *
     * @see Mapper#buildKey(int, int, int)
     * @return Pending block writes, mapped by keys of corresponding requests.
     * @since 1.0
     */
    public Map<Object, CompletableFuture<BlockWrite>> getPendingWrites() {
        return pendingWrites;
    }

    /**************************************************/
    // Methods below are not a part of the public API //
    /**************************************************/

    Queue<Request> getRequestQueue() {
        return requestQueue;
    }

    boolean initializedRequestQueue() {
        return initializedRequestQueue;
    }

    void setInitializedRequestQueue(boolean initializedRequestQueue) {
        this.initializedRequestQueue = initializedRequestQueue;
    }

    Optional<Assignment> getCurrentAssignment() {
        return assignment;
    }

    void setCurrentAssignment(Assignment assignment) {
        this.assignment = Optional.of(assignment);
    }

    void removeAssignment() {
        this.assignment = Optional.empty();
    }
}
