package bt.dht;

import bt.module.ServiceModule;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;


public class DHTModule implements Module {

    private DHTConfig config;

    public DHTModule() {
        this.config = new DHTConfig();
    }

    public DHTModule(DHTConfig config) {
        this.config = config;
    }

    @Override
    public void configure(Binder binder) {
        ServiceModule.contributePeerSourceFactory(binder).addBinding().to(DHTPeerSourceFactory.class);
    }

    @Provides
    @Singleton
    public DHTService provideDHTService(IRuntimeLifecycleBinder lifecycleBinder) {
        return new MldhtService(lifecycleBinder, config);
    }
}
