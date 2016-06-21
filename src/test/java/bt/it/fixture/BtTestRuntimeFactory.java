package bt.it.fixture;

import bt.BtRuntime;
import bt.service.BaseShutdownService;
import bt.service.IShutdownService;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class BtTestRuntimeFactory extends ExternalResource {

    private final List<BtRuntime> knownRuntimes;

    public BtTestRuntimeFactory() {
        knownRuntimes = new ArrayList<>();
    }

    public BtTestRuntimeBuilder buildRuntime(InetAddress address, int port) {
        return new BtTestRuntimeBuilder(address, port) {
            @Override
            public BtRuntime build() {
                BtRuntime runtime = super.build();
                knownRuntimes.add(runtime);
                return runtime;
            }
        };
    }

    @Override
    protected void after() {
        knownRuntimes.forEach(runtime -> {
            runtime.service(IShutdownService.class).shutdownNow();
        });
    }

    public static class OnDemandShutdownService extends BaseShutdownService {

        private static final Logger LOGGER = LoggerFactory.getLogger(OnDemandShutdownService.class);

        protected OnDemandShutdownService() {
            super(Duration.ofMillis(100));
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
}
