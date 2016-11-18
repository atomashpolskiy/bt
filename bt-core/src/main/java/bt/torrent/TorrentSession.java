package bt.torrent;

import bt.metainfo.Torrent;

/**
 * Torrent processing session.
 *
 * @since 1.0
 */
public interface TorrentSession {

    /**
     * @return Torrent, that this session is processing
     * @since 1.0
     */
    Torrent getTorrent();

    /**
     * @return Current state of torrent processing session
     * @since 1.0
     */
    TorrentSessionState getState();
}
