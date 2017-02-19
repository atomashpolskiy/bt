package bt;

import bt.runtime.BtRuntime;

import java.util.Objects;

/**
 * Builds a client and attaches it to the provided runtime.
 *
 * @since 1.1
 */
public class BtClientBuilder extends BaseClientBuilder<BtClientBuilder> {

    static BtClientBuilder runtime(BtRuntime runtime) {
        return new BtClientBuilder(runtime);
    }

    private BtRuntime runtime;

    private BtClientBuilder(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Missing runtime");
    }

    @Override
    protected BtRuntime getRuntime() {
        return runtime;
    }
}
