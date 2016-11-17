package bt.service;

import bt.metainfo.Torrent;

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
     */
    PeerSource getPeerSource(Torrent torrent);
}
