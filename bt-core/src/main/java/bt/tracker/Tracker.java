package bt.tracker;

import bt.metainfo.Torrent;

/**
 * Generic tracker interface.
 *
 * @since 1.0
 */
public interface Tracker {

    /**
     * Build a tracker request for a given torrent.
     *
     * @return Tracker request builder
     * @since 1.0
     */
    TrackerRequestBuilder request(Torrent torrent);
}
