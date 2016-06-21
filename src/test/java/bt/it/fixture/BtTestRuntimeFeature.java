package bt.it.fixture;

import com.google.inject.Binder;

public interface BtTestRuntimeFeature {

    void contributeToRuntime(BtTestRuntimeBuilder runtimeBuilder, Binder binder);
}
