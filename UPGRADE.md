# UPGRADE INSTRUCTIONS

---

## 1.5

* `bt.BaseClientBuilder#runtime(BtRuntime)` is now protected instead of public. Use a factory method `bt.Bt#client(BtRuntime)` to attach the newly created client to a shared runtime.
* Static contribution methods in `bt.module.ServiceModule` and `bt.module.ProtocolModule` has been replaced by _module extenders_, which provide a clearer and more concise API for contributing custom extensions to the core. Instead of invoking individual contributions methods, downstream modules should now call `bt.module.ServiceModule#extend(Binder)` or `bt.module.ProtocolModule#extend(Binder)` and use methods in the returned builder instance, e.g.:

```java
import com.google.inject.Binder;
import com.google.inject.Module;

import bt.module.ProtocolModule;

public class MyModule implements Module {
    
    @Override
    public void configure(Binder binder) {
        ProtocolModule.extend(binder)
            .addMessageHandler(20, ExtendedProtocol.class)
            .addExtendedMessageHandler("ut_metadata", UtMetadataMessageHandler.class);
    }
}
```