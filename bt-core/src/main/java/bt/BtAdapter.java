package bt;

import com.google.inject.Binder;

public interface BtAdapter {

    void contributeToRuntime(Binder binder);
}
