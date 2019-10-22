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

import bt.net.Peer;
import bt.data.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.selector.PieceSelector;

import java.time.Duration;
import java.util.*;

class Assignment {

    enum Status { ACTIVE, DONE, TIMEOUT };

    private Peer peer;
    private PieceSelector selector;
    private BitfieldBasedStatistics pieceStatistics;
    private Assignments assignments;

    private Queue<Integer> pieces;
    private ConnectionState connectionState;

    private final Duration limit;

    private long started;
    private long checked;

    private boolean aborted;

    Assignment(Peer peer, Duration limit, PieceSelector selector,
               BitfieldBasedStatistics pieceStatistics, Assignments assignments) {
        this.peer = peer;
        this.selector = selector;
        this.pieceStatistics = pieceStatistics;
        this.assignments = assignments;

        this.limit = limit;
        this.pieces = new ArrayDeque<>();

        claimPiecesIfNeeded();
    }

    Peer getPeer() {
        return peer;
    }

    Queue<Integer> getPieces() {
        return pieces;
    }

    private void claimPiecesIfNeeded() {
        // TODO: change this to a configurable setting?
        int n = 3;
        if (pieces.size() < n) {
            Bitfield peerBitfield = pieceStatistics.getPeerBitfield(peer).get();
            Iterator<Integer> iter = selector.getNextPieces(pieceStatistics).iterator();
            while (iter.hasNext() && pieces.size() < 3) {
                // TODO: during endgame we need to choose the next piece randomly (see commented code in Assignments)
                Integer pieceIndex = iter.next();
                if (peerBitfield.isVerified(pieceIndex) && assignments.claim(pieceIndex)) {
                    pieces.add(pieceIndex);
                }
            }
        }
    }

    boolean isAssigned(int pieceIndex) {
        return pieces.contains(pieceIndex);
    }

    Status getStatus() {
        if (started > 0) {
            long duration = System.currentTimeMillis() - checked;
            if (duration > limit.toMillis()) {
                return Status.TIMEOUT;
            }
        }
        return Status.ACTIVE;
    }

    void start(ConnectionState connectionState) {
        if (this.connectionState != null) {
            throw new IllegalStateException("Assignment is already started");
        }
        if (aborted) {
            throw new IllegalStateException("Assignment is aborted");
        }
        this.connectionState = connectionState;
        connectionState.setCurrentAssignment(this);
        started = System.currentTimeMillis();
        checked = started;
    }

    void check() {
        checked = System.currentTimeMillis();
    }

    void finish(Integer pieceIndex) {
        if (pieces.remove(pieceIndex)) {
            assignments.finish(pieceIndex);
            claimPiecesIfNeeded();
        }
    }

    void abort() {
        aborted = true;
        if (connectionState != null) {
            connectionState.removeAssignment();
        }
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "pieces=" + pieces +
                ", peer=" + peer +
                ", started=" + started +
                ", limit=" + limit +
                ", checked=" + checked +
                ", aborted=" + aborted +
                '}';
    }
}
