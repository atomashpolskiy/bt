package bt;

import com.google.inject.Injector;

public class BtRuntime {

    private Injector injector;

    BtRuntime(Injector injector) {
        this.injector = injector;
    }

    public <T> T service(Class<T> serviceType) {
        return injector.getInstance(serviceType);
    }
}
