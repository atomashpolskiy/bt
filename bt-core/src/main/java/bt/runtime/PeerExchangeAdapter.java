package bt.runtime;

import bt.BtAdapter;
import bt.runtime.service.ext.pex.PeerExchangePeerSourceFactory;
import bt.service.PeerSourceFactory;
import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class PeerExchangeAdapter implements BtAdapter {

    @Override
    public void contributeToRuntime(Binder binder) {
        Multibinder.newSetBinder(binder, PeerSourceFactory.class).addBinding().to(PeerExchangePeerSourceFactory.class);
    }
}
