package bt.it.fixture;

import bt.runtime.BtRuntime;
import bt.runtime.Config;
import org.junit.rules.ExternalResource;

import java.util.ArrayList;
import java.util.List;

public class BtTestRuntimeFactory extends ExternalResource {

    private final List<BtRuntime> knownRuntimes;

    public BtTestRuntimeFactory() {
        knownRuntimes = new ArrayList<>();
    }

    public BtTestRuntimeBuilder buildRuntime(Config config) {
        return new BtTestRuntimeBuilder(config) {
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
        knownRuntimes.forEach(BtRuntime::shutdown);
    }
}
