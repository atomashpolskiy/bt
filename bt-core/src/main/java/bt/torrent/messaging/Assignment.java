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

package bt.torrent.messaging;

import bt.data.LocalBitfield;
import bt.data.PeerBitfield;
import bt.net.ConnectionKey;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.selector.ShuffleUtils;
import bt.torrent.selector.ValidatingSelector;
import com.google.common.base.MoreObjects;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Queue;

class Assignment {
    private final int maxSimultaneouslyAssignedPieces;

    enum Status {ACTIVE, TIMEOUT}

    ;

    private final LocalBitfield localBitfield;
    private final ConnectionKey connectionKey;
    private final ValidatingSelector selector;
    private final BitfieldBasedStatistics pieceStatistics;
    private final Assignments assignments;

    private final Queue<Integer> pieces;
    private ConnectionState connectionState;

    private final Duration limit;

    private long started;
    private long checked;

    private boolean aborted;

    Assignment(ConnectionKey connectionKey, int maxSimultaneouslyAssignedPieces, Duration limit,
               ValidatingSelector selector, LocalBitfield localBitfield,
               BitfieldBasedStatistics pieceStatistics, Assignments assignments) {
        this.localBitfield = localBitfield;
        this.connectionKey = connectionKey;
        this.selector = selector;
        this.pieceStatistics = pieceStatistics;
        this.assignments = assignments;

        this.maxSimultaneouslyAssignedPieces = maxSimultaneouslyAssignedPieces;
        this.limit = limit;
        this.pieces = new ArrayDeque<>();

        claimPiecesIfNeeded();
    }

    ConnectionKey getConnectionKey() {
        return connectionKey;
    }

    Queue<Integer> getPieces() {
        return pieces;
    }

    private void claimPiecesIfNeeded() {
        if (pieces.size() < maxSimultaneouslyAssignedPieces) {
            final int numPiecesToAdd = maxSimultaneouslyAssignedPieces - pieces.size();
            PeerBitfield peerBitfield = pieceStatistics.getPeerBitfield(connectionKey).get();
            if (!assignments.isEndgame()) {
                BitSet relevantPieces = peerBitfield.getBitmask(); //returns a copy
                localBitfield.removeVerifiedPiecesFromBitset(relevantPieces);
                selector.getNextPieces(peerBitfield, pieceStatistics)
                        .filter(pieceIndex -> assignments.claim(pieceIndex))
                        .limit(numPiecesToAdd)
                        .forEach(pieceIndex -> pieces.add(pieceIndex));
            } else {
                // randomize order of pieces to keep the number of pieces
                // requested from different peers at the same time to a minimum
                int[] requiredPieces = selector.getNextPieces(peerBitfield, pieceStatistics).toArray();
                ShuffleUtils.shuffle(requiredPieces);

                for (int i = 0; i < Math.min(numPiecesToAdd, requiredPieces.length); i++) {
                    int pieceIndex = requiredPieces[i];
                    if (peerBitfield.isVerified(pieceIndex) && assignments.claim(pieceIndex)) {
                        pieces.add(pieceIndex);
                    }
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
        return MoreObjects.toStringHelper(this)
                .add("connectionKey", connectionKey)
                .add("pieces", pieces)
                .add("limit", limit)
                .add("started", started)
                .add("checked", checked)
                .add("aborted", aborted)
                .toString();
    }
}
