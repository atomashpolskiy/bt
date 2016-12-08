package bt.it.fixture;

import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import com.google.inject.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class BtRuntimeFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtRuntimeFactory.class);

    private Collection<Module> modules;
    private List<BtRuntime> knownRuntimes;

    BtRuntimeFactory(Collection<Module> modules) {
        this.modules = modules;
        this.knownRuntimes = new ArrayList<>();
    }

    public BtRuntimeBuilder builder(Config config) {
        return new BtRuntimeBuilder(config) {
            @Override
            public BtRuntime build() {
                modules.forEach(super::module);
                BtRuntime runtime = super.build();
                knownRuntimes.add(runtime);
                return runtime;
            }
        };
    }

    public void shutdown() {
        knownRuntimes.forEach(runtime -> {
            try {
                runtime.shutdown();
            } catch (Exception e) {
                LOGGER.error("Runtime failed to shutdown", e);
            }
        });
    }
}
