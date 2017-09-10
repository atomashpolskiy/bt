package bt.event;

import bt.data.Bitfield;
import bt.metainfo.TorrentId;
import bt.net.Peer;

/**
 * @since 1.5
 */
public interface EventSink {

    /**
     * @since 1.5
     */
    void firePeerDiscovered(TorrentId torrentId, Peer peer);

    /**
     * @since 1.5
     */
    void firePeerConnected(TorrentId torrentId, Peer peer);

    /**
     * @since 1.5
     */
    void firePeerDisconnected(TorrentId torrentId, Peer peer);

    /**
     * @since 1.5
     */
    void firePeerBitfieldUpdated(TorrentId torrentId, Peer peer, Bitfield bitfield);

    /**
     * Announce that the torrent has been started.
     *
     * @since 1.5
     */
    void fireTorrentStarted(TorrentId torrentId);

    /**
     * Announce that the torrent has been stopped.
     *
     * @since 1.5
     */
    void fireTorrentStopped(TorrentId torrentId);
}
