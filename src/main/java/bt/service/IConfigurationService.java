package bt.service;

public interface IConfigurationService {

    long getHandshakeTimeOut();

    long getConnectionTimeOut();

    int getMaxActiveConnectionsPerTorrent();

    long getPeerRefreshThreshold();

    long getTransferBlockSize();

    long getMaxPeerInactivityInterval();
}
