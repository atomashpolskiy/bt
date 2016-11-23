package bt.peerexchange.service;

import bt.net.Peer;

class PeerEvent implements Comparable<PeerEvent> {

    enum Type { ADDED, DROPPED }

    static PeerEvent added(Peer peer) {
        return new PeerEvent(Type.ADDED, peer);
    }

    static PeerEvent dropped(Peer peer) {
        return new PeerEvent(Type.DROPPED, peer);
    }

    private Type type;
    private Peer peer;
    private long instant;

    private PeerEvent(Type type, Peer peer) {

        this.type = type;
        this.peer = peer;

        instant = System.currentTimeMillis();
    }

    Type getType() {
        return type;
    }

    Peer getPeer() {
        return peer;
    }

    long getInstant() {
        return instant;
    }

    @Override
    public int compareTo(PeerEvent o) {

        if (instant == o.getInstant()) {
            return 0;
        } else if (instant - o.getInstant() >= 0) {
            return 1;
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "PeerEvent {type=" + type + ", peer=" + peer + ", instant=" + instant + '}';
    }
}
