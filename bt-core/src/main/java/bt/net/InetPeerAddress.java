package bt.net;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Holds parts of inet address and resolves it on demand.
 * Helps prevent unsolicited blocking that can happen when directly creating an {@link InetSocketAddress}.
 *
 * @since 1.3
 */
public class InetPeerAddress {

    private final String hostname;
    private final int port;
    private final int hashCode;

    private volatile InetSocketAddress address;
    private final Object lock;

    /**
     * @since 1.3
     */
    public InetPeerAddress(String hostname, int port) {
        this.hostname = Objects.requireNonNull(hostname);
        this.port = port;
        this.hashCode = 31 * hostname.hashCode() + port;
        this.lock = new Object();
    }

    /**
     * @since 1.3
     */
    public InetSocketAddress getAddress() {
        if (address == null) {
            synchronized (lock) {
                if (address == null) {
                    address = new InetSocketAddress(hostname, port);
                }
            }
        }
        return address;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || !getClass().equals(object.getClass())) {
            return false;
        }

        InetPeerAddress that = (InetPeerAddress) object;
        return port == that.port && hostname.equals(that.hostname);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
