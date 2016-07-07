package bt.it.fixture;

import bt.BtRuntimeBuilder;

public interface BtTestRuntimeFeature {

    void contributeToRuntime(BtTestRuntimeConfiguration configuration, BtRuntimeBuilder runtimeBuilder);
}
