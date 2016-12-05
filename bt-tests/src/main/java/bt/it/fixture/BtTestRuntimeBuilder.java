package bt.it.fixture;

import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;

public class BtTestRuntimeBuilder {

    private Config config;

    private BtRuntimeBuilder builder;
    private Collection<BtTestRuntimeFeature> features;

    protected BtTestRuntimeBuilder(Config config) {
        this.config = config;
        this.builder = BtRuntime.builder(config);
    }

    public BtTestRuntimeBuilder feature(BtTestRuntimeFeature feature) {
        if (features == null) {
            features = new ArrayList<>();
        }
        features.add(feature);
        return this;
    }

    public BtRuntime build() {

        if (features != null) {
            BtTestRuntimeConfiguration configuration = new BtTestRuntimeConfiguration() {
                @Override
                public InetAddress getAddress() {
                    return config.getAcceptorAddress();
                }

                @Override
                public int getPort() {
                    return config.getAcceptorPort();
                }
            };
            features.forEach(feature -> feature.contributeToRuntime(configuration, builder));
        }

        return builder.build();
    }
}
