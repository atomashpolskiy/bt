package bt.it.fixture;

import bt.service.ConfigurationService;
import bt.service.IConfigurationService;
import com.google.inject.Binder;

public class TestConfigurationFeature implements BtTestRuntimeFeature {

    @Override
    public void contributeToRuntime(BtTestRuntimeBuilder runtimeBuilder, Binder binder) {
        binder.bind(IConfigurationService.class).toInstance(
            new ConfigurationService() {
                @Override
                public long getPeerRefreshThreshold() {
                    return 5000;
                }
            });
    }
}
