package bt.it.fixture;

import bt.runtime.BtRuntimeBuilder;

public interface BtTestRuntimeFeature {

    void contributeToRuntime(BtTestRuntimeConfiguration configuration, BtRuntimeBuilder runtimeBuilder);
}
