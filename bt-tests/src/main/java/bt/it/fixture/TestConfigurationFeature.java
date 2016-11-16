package bt.it.fixture;

import bt.runtime.BtRuntimeBuilder;
import bt.service.ConfigurationService;
import bt.service.IConfigurationService;

public class TestConfigurationFeature implements BtTestRuntimeFeature {

    @Override
    public void contributeToRuntime(BtTestRuntimeConfiguration configuration, BtRuntimeBuilder runtimeBuilder) {
        runtimeBuilder.module(binder ->
                binder.bind(IConfigurationService.class).toInstance(
                            new ConfigurationService() {
                                @Override
                                public long getPeerRefreshThreshold() {
                                    return 5000;
                                }
        }));
    }
}
