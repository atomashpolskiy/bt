package bt.peerexchange;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class PeerExchangeModule implements Module {

    private PeerExchangeConfig config;

    public PeerExchangeModule() {
        this.config = new PeerExchangeConfig();
    }

    public PeerExchangeModule(PeerExchangeConfig config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {

        binder.bind(PeerExchangeConfig.class).toInstance(config);

        // explicit singleton binding is required to prevent multibinders from creating 2 instances
        // see https://github.com/google/guice/issues/791
        // also, this service contributes startup lifecycle bindings and should be instantiated eagerly
        binder.bind(PeerExchangePeerSourceFactory.class).asEagerSingleton();

        ServiceModule.contributePeerSourceFactory(binder).addBinding()
                .to(PeerExchangePeerSourceFactory.class);

        ServiceModule.contributeMessagingAgent(binder).addBinding()
                .to(PeerExchangePeerSourceFactory.class);

        ProtocolModule.contributeExtendedMessageHandler(binder)
                .addBinding("ut_pex").to(PeerExchangeMessageHandler.class);
    }
}
