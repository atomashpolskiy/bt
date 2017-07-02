package bt.torrent;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

/**
 * Torrent session factory.
 *
 * @since 1.0
 */
public interface ITorrentSessionFactory {

    /**
     * Create a session for a given torrent.
     *
     * @param torrent Torrent (must be registered in {@link TorrentRegistry})
     * @param params Torrent-specific parameters and configuration
     * @return Torrent session
     * @since 1.0
     */
    TorrentSession createSession(Torrent torrent, TorrentSessionParams params);

    /**
     * Create a session for a given torrent ID.
     *
     * @param torrentId Torrent ID (must be registered {@link TorrentRegistry})
     * @param params Torrent-specific parameters and configuration
     * @return Torrent session
     * @since 1.3
     */
    TorrentSession createSession(TorrentId torrentId, TorrentSessionParams params);
}
