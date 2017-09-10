package bt.event;

import bt.metainfo.TorrentId;

public class TorrentStoppedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;

    protected TorrentStoppedEvent(long id, long timestamp, TorrentId torrentId) {
        super(id, timestamp);
        this.torrentId = torrentId;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + getId() + "}, timestamp {" + getTimestamp() +
                "}, torrent {" + torrentId + "}";
    }
}
