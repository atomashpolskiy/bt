package bt.module;

import bt.runtime.protocol.ext.pex.PeerExchangeMessageHandler;
import bt.runtime.service.ext.pex.PeerExchangePeerSourceFactory;
import bt.service.PeerSourceFactory;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

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
