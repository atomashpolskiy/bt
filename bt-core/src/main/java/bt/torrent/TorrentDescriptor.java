package bt.torrent;

import bt.data.DataDescriptor;

/**
 * Provides an interface for controlling
 * the state of a torrent processing session.
 *
 * @since 1.0
 */
public interface TorrentDescriptor {

    /**
     * @return true if the torrent is currently being processed
     * @since 1.0
     */
    boolean isActive();

    /**
     * Issue a request to begin torrent processing
     *
     * @since 1.0
     */
    void start();

    /**
     * Issue a request to stop torrent processing
     *
     * @since 1.0
     */
    void stop();

    /**
     * Signal that the torrent has been successfully downloaded and verified
     *
     * @since 1.0
     */
    void complete();

    /**
     * @return Torrent data descriptor or null, if it hasn't been created yet
     * @since 1.0
     */
    DataDescriptor getDataDescriptor();
}
