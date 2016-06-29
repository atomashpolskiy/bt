package bt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class OnDemandShutdownService extends BaseShutdownService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnDemandShutdownService.class);

    protected OnDemandShutdownService() {
        super(Duration.ofMillis(1000));
    }

    @Override
    protected void onError(Throwable e) {
        LOGGER.error("Error on shutdown", e);
    }

    @Override
    public void shutdownNow() {
        shutdown();
    }
}
