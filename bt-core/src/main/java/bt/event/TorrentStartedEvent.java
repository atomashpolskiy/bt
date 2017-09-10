package bt.event;

import bt.metainfo.TorrentId;

public class TorrentStartedEvent extends BaseEvent implements TorrentEvent {

    private final TorrentId torrentId;

    protected TorrentStartedEvent(long id, long timestamp, TorrentId torrentId) {
        super(id, timestamp);
        this.torrentId = torrentId;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }
}
