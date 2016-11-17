package bt.data;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

/**
 * Data back-end for torrent files
 *
 * @since 1.0
 */
public interface Storage {

    /**
     * Get a storage unit for a particular torrent file
     *
     * @param torrent Torrent descriptor
     * @param torrentFile Torrent file descriptor
     * @return Storage unit for a single torrent file
     * @since 1.0
     */
    StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile);
}
