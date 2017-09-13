package bt.event;

import bt.metainfo.TorrentId;

/**
 * Generic interface for events, that are specific to some torrent.
 *
 * @since 1.5
 */
public interface TorrentEvent {

    /**
     * @since 1.5
     */
    TorrentId getTorrentId();
}
