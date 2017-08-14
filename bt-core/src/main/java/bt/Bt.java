package bt;

import bt.runtime.BtRuntime;

/**
 * Main entry point to building Bt clients
 *
 * @since 1.0
 */
public class Bt {

    /**
     * Create a standalone client builder with a private runtime
     *
     * @since 1.1
     */
    public static StandaloneClientBuilder client() {
        return new StandaloneClientBuilder();
    }

    /**
     * Create a standard client builder with the provided runtime
     *
     * @since 1.1
     */
    public static BtClientBuilder client(BtRuntime runtime) {
        return new BtClientBuilder().runtime(runtime);
    }
}
