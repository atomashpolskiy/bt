package bt.event;

import bt.data.Bitfield;
import bt.metainfo.TorrentId;
import bt.net.Peer;

public class PeerBitfieldUpdatedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;
    private final Peer peer;
    private final Bitfield bitfield;

    protected PeerBitfieldUpdatedEvent(long id, long timestamp, TorrentId torrentId, Peer peer, Bitfield bitfield) {
        super(id, timestamp);
        this.torrentId = torrentId;
        this.peer = peer;
        this.bitfield = bitfield;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    public Peer getPeer() {
        return peer;
    }

    public Bitfield getBitfield() {
        return bitfield;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, torrent {" + torrentId + "}, peer {" + peer + "}";
    }
}
