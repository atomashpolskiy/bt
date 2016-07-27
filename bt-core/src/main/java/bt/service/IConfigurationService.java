package bt.service;

public interface IConfigurationService {

    long getHandshakeTimeOut();

    long getConnectionTimeOut();

    int getMaxActiveConnectionsPerTorrent();

    long getPeerRefreshThreshold();

    long getTransferBlockSize();

    long getMaxTransferBlockSize();

    int getReadRequestQueueMaxLength();

    long getPeerBanTime();

    long getMaxPeerInactivityInterval();

    boolean shouldVerifyChunksOnInit();
}
