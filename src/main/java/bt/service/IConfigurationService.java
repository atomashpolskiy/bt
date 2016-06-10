package bt.service;

public interface IConfigurationService {

    long getHandshakeTimeOut();

    long getConnectionTimeOut();

    int getMaxActiveConnectionsPerTorrent();

    long getPeerRefreshThreshold();

    long getTransferBlockSize();

    int getReadRequestQueueMaxLength();

    long getPeerBanTime();

    long getMaxPeerInactivityInterval();
}
