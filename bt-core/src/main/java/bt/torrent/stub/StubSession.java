package bt.torrent.stub;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StubSession implements TorrentSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(StubSession.class);

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

    @Override
    public void onPeerDiscovered(Peer peer) {
        LOGGER.warn("onPeerDiscovered called on stub session");
    }

    @Override
    public void onPeerConnected(TorrentId torrentId, Peer peer) {
        LOGGER.warn("onPeerConnected called on stub session");
    }

    @Override
    public void onPeerDisconnected(TorrentId torrentId, Peer peer) {
        LOGGER.warn("onPeerDisconnected called on stub session");
    }
}
