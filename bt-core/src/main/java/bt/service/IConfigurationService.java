package bt.service;

/**
 * Provides common configuration parameters.
 *
 * @since 1.0
 */
public interface IConfigurationService {

    /**
     * @return Time to wait for a peer's handshake.
     * @since 1.0
     */
    long getHandshakeTimeOut();

    /**
     * @return Time to wait for establishing of a new peer connection.
     * @since 1.0
     */
    long getConnectionTimeOut();

    /**
     * @return Maximum number of simultaneous peer connections
     *         within a torrent processing session.
     *         Affects performance (too few or too many is bad).
     * @since 1.0
     */
    int getMaxActiveConnectionsPerTorrent();

    /**
     * @return Interval at which peer sources should be queried for new peers.
     * @since 1.0
     */
    long getPeerRefreshThreshold();

    /**
     * @return Network transfer block size
     * @since 1.0
     */
    long getTransferBlockSize();

    /**
     * @return Maximum supported transfer block size.
     *         Used by message buffers.
     * @since 1.0
     */
    long getMaxTransferBlockSize();

    /**
     * @return Maximum depth of I/O operations queue (read/write blocks).
     * @since 1.0
     */
    int getIOQueueMaxLength();

    /**
     * @return Time after which an inactive peer connection is dropped.
     * @since 1.0
     */
    long getMaxPeerInactivityInterval();

    /**
     * @return true if existing torrent files should be verified upon creating a data descriptor.
     * @since 1.0
     */
    boolean shouldVerifyChunksOnInit();
}
