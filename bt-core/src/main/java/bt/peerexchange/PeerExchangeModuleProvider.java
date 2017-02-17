package bt.peerexchange;

import bt.module.BtModuleProvider;
import com.google.inject.Module;

/**
 * @since 1.1
 */
public class PeerExchangeModuleProvider implements BtModuleProvider {

    @Override
    public Module module() {
        return new PeerExchangeModule();
    }
}
