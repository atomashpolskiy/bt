package bt.service;

public class ConfigurationService implements IConfigurationService {

    @Override
    public long getHandshakeTimeOut() {
        return 3000;
    }

    @Override
    public long getConnectionTimeOut() {
        return 0;
    }

    @Override
    public int getMaxActiveConnectionsPerTorrent() {
        return 20;
    }

    @Override
    public long getPeerRefreshThreshold() {
        return 300000;
    }

    @Override
    public long getTransferBlockSize() {
        return 2 << 13;
    }

    @Override
    public long getMaxTransferBlockSize() {
        return 2 << 16;
    }

    @Override
    public int getReadRequestQueueMaxLength() {
        return 10000;
    }

    @Override
    public long getPeerBanTime() {
        return 300000;
    }

    @Override
    public long getMaxPeerInactivityInterval() {
        return 180000;
    }

    @Override
    public boolean shouldVerifyChunksOnInit() {
        return true;
    }
}
