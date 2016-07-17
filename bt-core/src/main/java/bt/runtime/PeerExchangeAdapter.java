package bt.runtime;

import bt.BtAdapter;
import bt.runtime.service.ext.pex.PeerExchangePeerSource;
import bt.service.PeerSource;
import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class PeerExchangeAdapter implements BtAdapter {

    @Override
    public void contributeToRuntime(Binder binder) {
        Multibinder.newSetBinder(binder, PeerSource.class).addBinding().to(PeerExchangePeerSource.class);
    }
}
