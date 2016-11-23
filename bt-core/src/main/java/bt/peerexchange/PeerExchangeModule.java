package bt.peerexchange;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import bt.peerexchange.protocol.PeerExchangeMessageHandler;
import bt.peerexchange.service.PeerExchangePeerSourceFactory;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Singleton;

public class PeerExchangeModule implements Module {

    @Override
    public void configure(Binder binder) {

        ServiceModule.contributePeerSourceFactory(binder).addBinding()
                .to(PeerExchangePeerSourceFactory.class).in(Singleton.class);

        ProtocolModule.contributeExtendedMessageHandler(binder)
                .addBinding("ut_pex").to(PeerExchangeMessageHandler.class);
    }
}
