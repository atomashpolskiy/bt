package bt.event;

import bt.metainfo.TorrentId;

/**
 * @since 1.5
 */
public interface TorrentEvent {

    /**
     * @since 1.5
     */
    TorrentId getTorrentId();
}
