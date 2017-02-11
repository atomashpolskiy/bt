package bt.runtime;

import bt.service.NetworkUtil;

import java.net.InetAddress;
import java.time.Duration;

/**
 * Provides runtime configuration parameters.
 *
 * @since 1.0
 */
public class Config {

    private InetAddress acceptorAddress;
    private int acceptorPort;
    private Duration peerDiscoveryInterval;
    private Duration peerHandshakeTimeout;
    private Duration peerConnectionRetryInterval;
    private int peerConnectionRetryCount;
    private Duration peerConnectionTimeout;
    private Duration peerConnectionInactivityThreshold;
    private Duration trackerQueryInterval;
    private int maxPeerConnections;
    private int maxPeerConnectionsPerTorrent;
    private int transferBlockSize;
    private int maxTransferBlockSize;
    private int maxIOQueueSize;
    private Duration shutdownHookTimeout;
    private boolean shouldFailOnUnexpectedBlocks;

    /**
     * Create a config with default parameters.
     *
     * @since 1.0
     */
    public Config() {
        this.acceptorAddress = NetworkUtil.getInetAddressFromNetworkInterfaces();
        this.acceptorPort = 6891;
        this.peerDiscoveryInterval = Duration.ofSeconds(5);
        this.peerConnectionRetryInterval = Duration.ofMinutes(5);
        this.peerConnectionRetryCount = 3;
        this.peerConnectionTimeout = Duration.ofSeconds(30);
        this.peerHandshakeTimeout = Duration.ofSeconds(3);
        this.peerConnectionInactivityThreshold = Duration.ofMinutes(3);
        this.trackerQueryInterval = Duration.ofMinutes(5);
        this.maxPeerConnections = 500;
        this.maxPeerConnectionsPerTorrent = 20;
        this.transferBlockSize = 2 << 13; // 8 KB
        this.maxTransferBlockSize = 2 << 16; // 128 KB
        this.maxIOQueueSize = 1000;
        this.shutdownHookTimeout = Duration.ofSeconds(5);
        this.shouldFailOnUnexpectedBlocks = false;
    }

    /**
     * Clone the provided config.
     *
     * @param config Config to take parameters from.
     * @since 1.0
     */
    public Config(Config config) {
        this.acceptorAddress = config.getAcceptorAddress();
        this.acceptorPort = config.getAcceptorPort();
        this.peerDiscoveryInterval = config.getPeerDiscoveryInterval();
        this.peerConnectionRetryInterval = config.getPeerConnectionRetryInterval();
        this.peerConnectionRetryCount = config.getPeerConnectionRetryCount();
        this.peerConnectionTimeout = config.getPeerConnectionTimeout();
        this.peerHandshakeTimeout = config.getPeerHandshakeTimeout();
        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();
        this.trackerQueryInterval = config.getTrackerQueryInterval();
        this.maxPeerConnections = config.getMaxPeerConnections();
        this.maxPeerConnectionsPerTorrent = config.getMaxPeerConnectionsPerTorrent();
        this.transferBlockSize = config.getTransferBlockSize();
        this.maxTransferBlockSize = config.getMaxTransferBlockSize();
        this.maxIOQueueSize = config.getMaxIOQueueSize();
        this.shutdownHookTimeout = config.getShutdownHookTimeout();
        this.shouldFailOnUnexpectedBlocks = config.shouldFailOnUnexpectedBlocks();
    }

    /**
     * @param acceptorAddress Local link that will be used by the incoming connection acceptor.
     * @since 1.0
     */
    public void setAcceptorAddress(InetAddress acceptorAddress) {
        this.acceptorAddress = acceptorAddress;
    }

    /**
     * @since 1.0
     */
    public InetAddress getAcceptorAddress() {
        return acceptorAddress;
    }

    /**
     * @param acceptorPort Local port that will be used by the incoming connection acceptor.
     * @since 1.0
     */
    public void setAcceptorPort(int acceptorPort) {
        this.acceptorPort = acceptorPort;
    }

    /**
     * @since 1.0
     */
    public int getAcceptorPort() {
        return acceptorPort;
    }

    /**
     * @param peerDiscoveryInterval Interval at which peer sources should be queried for new peers.
     * @since 1.0
     */
    public void setPeerDiscoveryInterval(Duration peerDiscoveryInterval) {
        this.peerDiscoveryInterval = peerDiscoveryInterval;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerDiscoveryInterval() {
        return peerDiscoveryInterval;
    }

    /**
     * @param peerHandshakeTimeout  Time to wait for a peer's handshake.
     * @since 1.0
     */
    public void setPeerHandshakeTimeout(Duration peerHandshakeTimeout) {
        this.peerHandshakeTimeout = peerHandshakeTimeout;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerHandshakeTimeout() {
        return peerHandshakeTimeout;
    }

    /**
     * @param peerConnectionRetryInterval Interval at which attempts to connect to a peer will be performed
     * @since 1.0
     */
    public void setPeerConnectionRetryInterval(Duration peerConnectionRetryInterval) {
        this.peerConnectionRetryInterval = peerConnectionRetryInterval;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerConnectionRetryInterval() {
        return peerConnectionRetryInterval;
    }

    /**
     * @param peerConnectionRetryCount Max number of attempts to connect to a peer
     * @since 1.0
     */
    public void setPeerConnectionRetryCount(int peerConnectionRetryCount) {
        this.peerConnectionRetryCount = peerConnectionRetryCount;
    }

    /**
     * @since 1.0
     */
    public int getPeerConnectionRetryCount() {
        return peerConnectionRetryCount;
    }

    /**
     * @param peerConnectionTimeout Amount of time to wait for establishing of a peer connection
     * @since 1.0
     */
    public void setPeerConnectionTimeout(Duration peerConnectionTimeout) {
        this.peerConnectionTimeout = peerConnectionTimeout;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerConnectionTimeout() {
        return peerConnectionTimeout;
    }

    /**
     * @param peerConnectionInactivityThreshold Amount of time after which an inactive peer connection will be dropped
     * @since 1.0
     */
    public void setPeerConnectionInactivityThreshold(Duration peerConnectionInactivityThreshold) {
        this.peerConnectionInactivityThreshold = peerConnectionInactivityThreshold;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerConnectionInactivityThreshold() {
        return peerConnectionInactivityThreshold;
    }

    /**
     * @param trackerQueryInterval Interval at which trackers will be queried for peers.
     * @since 1.0
     */
    public void setTrackerQueryInterval(Duration trackerQueryInterval) {
        this.trackerQueryInterval = trackerQueryInterval;
    }

    /**
     * @since 1.0
     */
    public Duration getTrackerQueryInterval() {
        return trackerQueryInterval;
    }

    /**
     * @param maxPeerConnections Maximum amount of established peer connections
     * @since 1.0
     */
    public void setMaxPeerConnections(int maxPeerConnections) {
        this.maxPeerConnections = maxPeerConnections;
    }

    /**
     * @since 1.0
     */
    public int getMaxPeerConnections() {
        return maxPeerConnections;
    }

    /**
     * @param maxPeerConnectionsPerTorrent Maximum number of established peer connections
     *                                     within a torrent processing session.
     *                                     Affects performance (too few or too many is bad).
     * @since 1.0
     */
    public void setMaxPeerConnectionsPerTorrent(int maxPeerConnectionsPerTorrent) {
        this.maxPeerConnectionsPerTorrent = maxPeerConnectionsPerTorrent;
    }

    /**
     * @since 1.0
     */
    public int getMaxPeerConnectionsPerTorrent() {
        return maxPeerConnectionsPerTorrent;
    }

    /**
     * @param transferBlockSize Network transfer block size
     * @since 1.0
     */
    public void setTransferBlockSize(int transferBlockSize) {
        this.transferBlockSize = transferBlockSize;
    }

    /**
     * @since 1.0
     */
    public int getTransferBlockSize() {
        return transferBlockSize;
    }

    /**
     * @param maxTransferBlockSize Maximum supported transfer block size.
     * @since 1.0
     */
    public void setMaxTransferBlockSize(int maxTransferBlockSize) {
        this.maxTransferBlockSize = maxTransferBlockSize;
    }

    /**
     * @since 1.0
     */
    public int getMaxTransferBlockSize() {
        return maxTransferBlockSize;
    }

    /**
     * @param maxIOQueueSize Maximum depth of I/O operations queue (read/write blocks).
     */
    public void setMaxIOQueueSize(int maxIOQueueSize) {
        this.maxIOQueueSize = maxIOQueueSize;
    }

    /**
     * @since 1.0
     */
    public int getMaxIOQueueSize() {
        return maxIOQueueSize;
    }

    /**
     * @param shutdownHookTimeout Amount of time to wait for a shutdown hook to execute before killing it
     * @since 1.0
     */
    public void setShutdownHookTimeout(Duration shutdownHookTimeout) {
        this.shutdownHookTimeout = shutdownHookTimeout;
    }

    /**
     * @since 1.0
     */
    public Duration getShutdownHookTimeout() {
        return shutdownHookTimeout;
    }

    /**
     * @param shouldFailOnUnexpectedBlocks true if the peer connection should be terminated
     *                                     when an unexpected (not requested) block is received
     * @since 1.1
     */
    public void setShouldFailOnUnexpectedBlocks(boolean shouldFailOnUnexpectedBlocks) {
        this.shouldFailOnUnexpectedBlocks = shouldFailOnUnexpectedBlocks;
    }

    /**
     * @since 1.1
     */
    public boolean shouldFailOnUnexpectedBlocks() {
        return shouldFailOnUnexpectedBlocks;
    }
}
