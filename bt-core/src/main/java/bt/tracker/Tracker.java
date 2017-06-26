package bt.tracker;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

/**
 * Generic tracker interface.
 *
 * @since 1.0
 */
public interface Tracker {

    /**
     * Build a tracker request for a given torrent.
     *
     * @param torrent Torrent containing tracker info
     * @return Tracker request builder
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #request(TorrentId)}
     */
    default TrackerRequestBuilder request(Torrent torrent) {
        return request(torrent.getTorrentId());
    }

    /**
     * Build a tracker request for a given torrent.
     *
     * @param torrentId Torrent ID
     * @return Tracker request builder
     * @since 1.3
     */
    TrackerRequestBuilder request(TorrentId torrentId);
}
