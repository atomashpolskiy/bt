package bt.data;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

public interface DataAccessFactory {

    DataAccess getOrCreateDataAccess(Torrent torrent, TorrentFile torrentFile);
}
