package bt.service;

public interface IConfigurationService {

    long getHandshakeTimeOut();

    long getConnectionTimeOut();

    long getPeerRefreshThreshold();

    long getTransferBlockSize();
}
