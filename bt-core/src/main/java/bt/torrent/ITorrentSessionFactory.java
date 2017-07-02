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
     * @return Torrent session
     * @since 1.3
     */
    TorrentSession createSession(Torrent torrent);

    /**
     * Create a session for a given torrent ID.
     *
     * @param torrentId Torrent ID (must be registered {@link TorrentRegistry})
     * @return Torrent session
     * @since 1.3
     */
    TorrentSession createSession(TorrentId torrentId);
}
