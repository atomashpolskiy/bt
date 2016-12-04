package bt.torrent;

import bt.metainfo.Torrent;

/**
 * Torrent session factory.
 *
 * @since 1.0
 */
public interface ITorrentSessionFactory {

    /**
     * Create a session for a given torrent.
     *
     * @param torrent Torrent (must be available via {@link TorrentRegistry})
     * @param params Torrent-specific parameters and configuration
     * @return Torrent session
     * @since 1.0
     */
    TorrentSession createSession(Torrent torrent, TorrentSessionParams params);
}
