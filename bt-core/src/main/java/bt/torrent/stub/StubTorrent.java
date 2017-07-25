package bt.torrent.stub;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.metainfo.TorrentSource;
import bt.tracker.AnnounceKey;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class StubTorrent implements Torrent {
    private static final StubTorrent instance = new StubTorrent();

    public static StubTorrent instance() {
        return instance;
    }

    @Override
    public TorrentSource getSource() {
        // TODO: return correct source, when torrent serialization is implemented
        // also in yourip.mock.MockTorrent
        return null;
    }

    @Override
    public Optional<AnnounceKey> getAnnounceKey() {
        return Optional.empty();
    }

    @Override
    public TorrentId getTorrentId() {
        return TorrentId.fromBytes(new byte[20]);
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public long getChunkSize() {
        return 0;
    }

    @Override
    public Iterable<byte[]> getChunkHashes() {
        return Collections.emptyList();
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public List<TorrentFile> getFiles() {
        return Collections.emptyList();
    }

    @Override
    public boolean isPrivate() {
        return false;
    }
}
