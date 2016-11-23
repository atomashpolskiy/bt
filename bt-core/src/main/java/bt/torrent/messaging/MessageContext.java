package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.Peer;

import java.util.Optional;

/**
 * Provides basic information about the context of a message (both inbound and outbound).
 *
 * @since 1.0
 */
public class MessageContext {

    private Optional<TorrentId> torrentId;
    private Peer peer;
    private ConnectionState connectionState;

    MessageContext(Optional<TorrentId> torrentId, Peer peer, ConnectionState connectionState) {
        this.torrentId = torrentId;
        this.peer = peer;
        this.connectionState = connectionState;
    }

    /**
     * @return Optional torrent ID or empty if not applicable
     *         (e.g. if a message was received outside of a torrent processing session)
     * @since 1.0
     */
    public Optional<TorrentId> getTorrentId() {
        return torrentId;
    }

    /**
     * @return Remote peer
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }
}
