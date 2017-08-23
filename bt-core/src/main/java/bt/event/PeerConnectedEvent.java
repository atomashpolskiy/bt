package bt.event;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Objects;

public class PeerConnectedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Peer peer;

    PeerConnectedEvent(long id, long timestamp, TorrentId torrentId, Peer peer) {
        super(id, timestamp);
        this.torrentId = Objects.requireNonNull(torrentId);
        this.peer = Objects.requireNonNull(peer);
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    public Peer getPeer() {
        return peer;
    }
}
