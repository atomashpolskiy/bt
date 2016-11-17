package bt.protocol;

import bt.metainfo.Torrent;

/**
 * Factory of standard BitTorrent handshakes.
 *
 * @since 1.0
 */
public interface IHandshakeFactory {

    /**
     * Create a handshake, that can be used
     * to initialize peer connections for a given torrent.
     *
     * @since 1.0
     */
    Handshake createHandshake(Torrent torrent);
}
