package bt.torrent.stub;

import bt.net.Peer;
import bt.torrent.TorrentSessionState;

import java.util.Collections;
import java.util.Set;

public class StubSessionState implements TorrentSessionState {
    private static final StubSessionState instance = new StubSessionState();

    public static StubSessionState instance() {
        return instance;
    }

    @Override
    public int getPiecesTotal() {
        return 1;
    }

    @Override
    public int getPiecesRemaining() {
        return 1;
    }

    @Override
    public long getDownloaded() {
        return 0;
    }

    @Override
    public long getUploaded() {
        return 0;
    }

    @Override
    public Set<Peer> getConnectedPeers() {
        return Collections.emptySet();
    }
}
