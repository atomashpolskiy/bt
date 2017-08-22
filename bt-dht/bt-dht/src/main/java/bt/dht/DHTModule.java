package bt.dht;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import bt.protocol.handler.PortMessageHandler;
import com.google.inject.Binder;
import com.google.inject.Module;

/**
 * @since 1.1
 */
public class DHTModule implements Module {

    private DHTConfig config;

    /**
     * @since 1.1
     */
    public DHTModule() {
        this.config = new DHTConfig();
    }

    /**
     * @since 1.1
     */
    public DHTModule(DHTConfig config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(DHTConfig.class).toInstance(config);

        ServiceModule.extend(binder).addPeerSourceFactory(DHTPeerSourceFactory.class);
        ProtocolModule.extend(binder).addHandshakeHandler(DHTHandshakeHandler.class);
        ProtocolModule.extend(binder).addMessageHandler(PortMessageHandler.PORT_ID, PortMessageHandler.class);

        // this service contributes startup lifecycle bindings and should be instantiated eagerly
        binder.bind(DHTService.class).to(MldhtService.class).asEagerSingleton();
    }
}
