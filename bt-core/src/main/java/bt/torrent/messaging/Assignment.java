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
}
