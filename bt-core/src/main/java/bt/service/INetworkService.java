package bt.service;

import java.net.InetAddress;

/**
 * Provides basic network information.
 *
 * @since 1.0
 */
public interface INetworkService {

    /**
     * @return Local IP address, that current runtime is bound to.
     * @since 1.0
     */
    InetAddress getInetAddress();

    /**
     * @return Listening port for incoming connections.
     * @since 1.0
     */
    int getPort();
}
