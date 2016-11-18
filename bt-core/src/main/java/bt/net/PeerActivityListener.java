package bt.net;

import bt.metainfo.TorrentId;

/**
 * Listens for substantial peer events, like initial discovery,
 * establishing the connection and dropping the connection.
 *
 * @since 1.0
 */
public interface PeerActivityListener {

    /**
     * Invoked when a new peer is discovered.
     *
     * @param peer Remote peer
     * @since 1.0
     */
    void onPeerDiscovered(Peer peer);

    /**
     * Invoked when a new peer is connected and the handshake,
     * containing the torrent ID, is processed.
     *
     * @param torrentId ID of a torrent, that this peer
     *                  is interested in sharing or downloading
     * @param peer Remote peer
     * @since 1.0
     */
    void onPeerConnected(TorrentId torrentId, Peer peer);

    /**
     * Invoked when a peer connection is dropped.
     *
     * @param peer Remote peer
     * @since 1.0
     */
    void onPeerDisconnected(Peer peer);
}
