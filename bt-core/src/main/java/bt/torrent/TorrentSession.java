package bt.torrent;

import bt.metainfo.TorrentId;
import bt.net.PeerActivityListener;

/**
 * Torrent processing session.
 *
 * @since 1.0
 */
public interface TorrentSession extends PeerActivityListener {

    /**
     * @return Torrent, that this session is processing
     * @since 1.3
     */
    TorrentId getTorrentId();

    /**
     * @return Current state of torrent processing session
     * @since 1.0
     */
    TorrentSessionState getState();
}
