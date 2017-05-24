package yourip;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class YourIPModule implements Module {

    @Override
    public void configure(Binder binder) {
        ProtocolModule.contributeExtendedMessageHandler(binder).addBinding(YourIP.id()).to(YourIPMessageHandler.class);
        ServiceModule.contributeMessagingAgent(binder).addBinding().to(YourIPMessenger.class);
    }
}
