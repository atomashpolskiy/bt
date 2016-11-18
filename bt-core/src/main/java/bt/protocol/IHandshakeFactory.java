package bt.protocol;

import bt.metainfo.TorrentId;

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
    Handshake createHandshake(TorrentId torrentId);
}
