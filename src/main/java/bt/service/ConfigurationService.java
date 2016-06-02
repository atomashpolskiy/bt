package bt.service;

public class ConfigurationService implements IConfigurationService {

    @Override
    public long getHandshakeTimeOut() {
        return 0;
    }

    @Override
    public long getConnectionTimeOut() {
        return 0;
    }

    @Override
    public long getPeerRefreshThreshold() {
        return 0;
    }

    @Override
    public long getTransferBlockSize() {
        return 2 << 13;
    }
}
