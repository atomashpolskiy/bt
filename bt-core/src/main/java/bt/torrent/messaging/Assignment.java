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

import java.time.Duration;

class Assignment {

    enum Status { ACTIVE, DONE, TIMEOUT };

    private Peer peer;
    private Integer piece;
    private ConnectionState connectionState;

    private final Duration limit;

    private long started;
    private long checked;

    private boolean aborted;
    private boolean finished;

    Assignment(Peer peer, Integer piece, Duration limit) {
        this.peer = peer;
        this.piece = piece;
        this.limit = limit;
    }

    Peer getPeer() {
        return peer;
    }

    Integer getPiece() {
        return piece;
    }

    Status getStatus() {
        if (finished) {
            return Status.DONE;
        } else if (started > 0) {
            long duration = System.currentTimeMillis() - started;
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
        if (aborted || finished) {
            throw new IllegalStateException("Assignment is already done");
        }
        this.connectionState = connectionState;
        connectionState.setCurrentAssignment(this);
        started = System.currentTimeMillis();
    }

    void check() {
        checked = System.currentTimeMillis();
    }

    void finish() {
        finished = !aborted;
        if (finished && connectionState != null) {
            connectionState.removeAssignment();
        }
    }

    void abort() {
        aborted = !finished;
        if (aborted && connectionState != null) {
            connectionState.removeAssignment();
        }
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "piece=" + piece +
                ", peer=" + peer +
                ", started=" + started +
                ", limit=" + limit +
                ", checked=" + checked +
                ", aborted=" + aborted +
                ", finished=" + finished +
                '}';
    }
}
