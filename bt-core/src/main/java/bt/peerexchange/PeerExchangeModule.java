package bt.peerexchange;

import bt.module.Contribute;
import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import bt.peerexchange.protocol.PeerExchangeMessageHandler;
import bt.peerexchange.service.PeerExchangePeerSourceFactory;
import com.google.inject.Binder;
import com.google.inject.Module;

public class PeerExchangeModule implements Module {

    @Contribute(ProtocolModule.class)
    public void contributeToProtocols(ProtocolModule protocolModule) {
        protocolModule.addExtendedMessageHandler("ut_pex",  new PeerExchangeMessageHandler());
    }

    @Contribute(ServiceModule.class)
    public void contributeToServices(ServiceModule serviceModule) {
        serviceModule.addPeerSourceFactoryType(PeerExchangePeerSourceFactory.class);
    }

    @Override
    public void configure(Binder binder) {
        // do nothing...
    }
}
