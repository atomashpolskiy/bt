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
}
