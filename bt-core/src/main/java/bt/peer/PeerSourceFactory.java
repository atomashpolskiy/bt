package bt.peer;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

/**
 * Factory of peer sources.
 *
 * Can be used to provide peer sources that operate on a per-torrent basis
 * or need access to DI services.
 *
 * @since 1.0
 */
public interface PeerSourceFactory {

    /**
     * Create a peer source for a given torrent.
     * Implementations are free to return the same instance for all torrents.
     *
     * @since 1.0
     * @deprecated since 1.3 in favor of {@link #getPeerSource(TorrentId)}
     */
    default PeerSource getPeerSource(Torrent torrent) {
        return getPeerSource(torrent.getTorrentId());
    }

    /**
     * Create a peer source for a given torrent.
     * Implementations are free to return the same instance for all torrents.
     *
     * @since 1.3
     */
    PeerSource getPeerSource(TorrentId torrentId);
}
