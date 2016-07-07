package bt.it.fixture;

import bt.BtRuntimeBuilder;
import bt.service.ConfigurationService;
import bt.service.IConfigurationService;

public class TestConfigurationFeature implements BtTestRuntimeFeature {

    @Override
    public void contributeToRuntime(BtTestRuntimeConfiguration configuration, BtRuntimeBuilder runtimeBuilder) {
        runtimeBuilder.adapter(binder ->
                binder.bind(IConfigurationService.class).toInstance(
                            new ConfigurationService() {
                                @Override
                                public long getPeerRefreshThreshold() {
                                    return 5000;
                                }
        }));
    }
}
