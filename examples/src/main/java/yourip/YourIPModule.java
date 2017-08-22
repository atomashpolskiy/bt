package yourip;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class YourIPModule implements Module {

    @Override
    public void configure(Binder binder) {
        ServiceModule.extend(binder).addMessagingAgentType(YourIPMessenger.class);
        ProtocolModule.extend(binder).addExtendedMessageHandler(YourIP.id(), YourIPMessageHandler.class);
    }
}
