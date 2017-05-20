package yourip.mock;

import bt.data.Storage;
import bt.data.StorageUnit;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

public class MockStorage implements Storage {

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        return new MockStorageUnit();
    }
}
