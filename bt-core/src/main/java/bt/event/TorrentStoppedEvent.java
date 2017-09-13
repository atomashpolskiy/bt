package bt.event;

import bt.metainfo.TorrentId;

/**
 * Indicates, that processing of some torrent has finished.
 *
 * @since 1.5
 */
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
