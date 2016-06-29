package bt.it.fixture;

import bt.BtRuntime;
import bt.service.IShutdownService;
import org.junit.rules.ExternalResource;

import java.net.InetAddress;
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
}
