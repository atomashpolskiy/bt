package bt.it.fixture;

import bt.net.Peer;
import bt.peer.IPeerRegistry;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;

import java.io.Closeable;
import java.io.IOException;

/**
 * Swarm participant.
 *
 * <p>All instances of this class are closed by the containing swarm,
 * so it's not necessary to call {@link #close()} in tests.
 *
 * @since 1.0
 */
public abstract class SwarmPeer implements Closeable {

    protected BtRuntime runtime;

    /**
     * Create a swarm participant with the provided runtime.
     * The created instance should be the exclusive user of this runtime
     * (i.e. no other swarm participants should be created with the same runtime).
     *
     * @param runtime Bt runtime, that this peer will attach to
     * @since 1.0
     */
    protected SwarmPeer(BtRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Get standard Bt peer, representing this swarm participant.
     *
     * @return Unique peer instance, representing this swarm participant
     * @since 1.0
     */
    public Peer getPeer() {
        return runtime.service(IPeerRegistry.class).getLocalPeer();
    }

    /**
     * Get Bt client instance for controlling this swarm participant.
     *
     * @return Bt client instance for controlling this swarm participant.
     * @since 1.0
     */
    public abstract BtClient getHandle();

    /**
     * @return true if this swarm participant is a seeder.
     * @since 1.0
     */
    public abstract boolean isSeeding();

    @Override
    public void close() throws IOException {
        runtime.shutdown();
    }
}
