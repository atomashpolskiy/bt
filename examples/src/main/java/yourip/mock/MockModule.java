package yourip.mock;

import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class MockModule implements Module {

    @Override
    public void configure(Binder binder) {
        ServiceModule.contributeTrackerFactory(binder).addBinding(MockTrackerFactory.schema()).to(MockTrackerFactory.class);
    }
}
