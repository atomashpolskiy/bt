package bt.torrent;

import bt.net.Peer;

import java.util.Set;

/**
 * Provides information about a particular torrent session.
 *
 * @since 1.0
 */
public interface TorrentSessionState {

    /**
     * @return Total number of pieces in the torrent
     * @since 1.0
     */
    int getPiecesTotal();

    /**
     * @return Number of pieces, that the local client does not have yet
     * @since 1.0
     */
    int getPiecesRemaining();

    /**
     * @return Amount of data downloaded via this session (in bytes)
     * @since 1.0
     */
    long getDownloaded();

    /**
     * @return Amount of data uploaded via this session (in bytes)
     * @since 1.0
     */
    long getUploaded();

    /**
     * @return Collection of peers, that this session is connected to
     * @since 1.0
     */
    Set<Peer> getConnectedPeers();
}
