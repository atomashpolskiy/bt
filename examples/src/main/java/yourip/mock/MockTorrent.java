package yourip.mock;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.metainfo.TorrentId;
import bt.metainfo.TorrentSource;
import bt.tracker.AnnounceKey;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MockTorrent implements Torrent {

    private static final byte[] id = new byte[20];
    private static final TorrentFile file = new TorrentFile() {
        @Override
        public long getSize() {
            return 1;
        }

        @Override
        public List<String> getPathElements() {
            return Collections.singletonList("file.mock");
        }
    };

    @Override
    public TorrentSource getSource() {
        // TODO: return correct source, when torrent serialization is implemented
        // also in bt.torrent.stub.StubTorrent
        return new TorrentSource() {
            @Override
            public Optional<byte[]> getMetadata() {
                return Optional.empty();
            }

            @Override
            public byte[] getExchangedMetadata() {
                return new byte[1];
            }
        };
    }

    @Override
    public Optional<AnnounceKey> getAnnounceKey() {
        return Optional.of(new AnnounceKey(MockTracker.url()));
    }

    @Override
    public TorrentId getTorrentId() {
        return TorrentId.fromBytes(id);
    }

    @Override
    public String getName() {
        return "Mock torrent";
    }

    @Override
    public long getChunkSize() {
        return 1;
    }

    @Override
    public Iterable<byte[]> getChunkHashes() {
        return Collections.singleton(id);
    }

    @Override
    public long getSize() {
        return 1;
    }

    @Override
    public List<TorrentFile> getFiles() {
        return Collections.singletonList(file);
    }

    @Override
    public boolean isPrivate() {
        return false;
    }
}
