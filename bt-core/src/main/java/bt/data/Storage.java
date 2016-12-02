package bt.data;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

/**
 * Data back-end. Provides storage for torrent files.
 *
 * @since 1.0
 */
public interface Storage {

    /**
     * Get a storage unit for a particular torrent file.
     *
     * @param torrent Torrent metainfo
     * @param torrentFile Torrent file metainfo
     * @return Storage unit for a single torrent file
     * @since 1.0
     */
    StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile);
}
