package bt.peerexchange;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import bt.peerexchange.protocol.PeerExchangeMessageHandler;
import bt.peerexchange.service.PeerExchangePeerSourceFactory;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Singleton;

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
        binder.bind(PeerExchangePeerSourceFactory.class).in(Singleton.class);

        ServiceModule.contributePeerSourceFactory(binder).addBinding()
                .to(PeerExchangePeerSourceFactory.class);

        ServiceModule.contributeMessagingAgent(binder).addBinding()
                .to(PeerExchangePeerSourceFactory.class);

        ProtocolModule.contributeExtendedMessageHandler(binder)
                .addBinding("ut_pex").to(PeerExchangeMessageHandler.class);
    }
}
