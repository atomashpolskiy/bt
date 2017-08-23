package bt.torrent.stub;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;

public class StubSession implements TorrentSession {

    private static final StubSession instance = new StubSession();

    public static StubSession instance() {
        return instance;
    }

    @Override
    public Torrent getTorrent() {
        return StubTorrent.instance();
    }

    @Override
    public TorrentId getTorrentId() {
        return StubTorrent.instance().getTorrentId();
    }

    @Override
    public TorrentSessionState getState() {
        return StubSessionState.instance();
    }
}
