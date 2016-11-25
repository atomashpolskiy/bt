package bt.it.fixture;

import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import bt.service.INetworkService;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;

public class BtTestRuntimeBuilder {

    private InetAddress address;
    private int port;

    private BtRuntimeBuilder builder;
    private Collection<BtTestRuntimeFeature> features;

    protected BtTestRuntimeBuilder(Config config, InetAddress address, int port) {

        this.address = address;
        this.port = port;

        builder = BtRuntime.builder(config);
        builder.module(binder -> {
            binder.bind(INetworkService.class).toInstance(new INetworkService() {
                @Override
                public InetAddress getInetAddress() {
                    return address;
                }

                @Override
                public int getPort() {
                    return port;
                }
            });
        });
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
                    return address;
                }

                @Override
                public int getPort() {
                    return port;
                }
            };
            features.forEach(feature -> feature.contributeToRuntime(configuration, builder));
        }

        return builder.build();
    }
}
