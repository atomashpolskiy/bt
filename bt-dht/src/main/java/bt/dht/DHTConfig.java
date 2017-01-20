package bt.dht;

/**
 * Provides configuration for DHT facilities.
 *
 * @since 1.1
 */
public class DHTConfig {

    private int listeningPort;
    private boolean useRouterBootstrap;
    private boolean useIPv6;

    /**
     * @since 1.1
     */
    public DHTConfig() {
        this.listeningPort = 49001;
        this.useRouterBootstrap = false;
        this.useIPv6 = false;
    }

    /**
     * @see #setListeningPort(int)
     * @since 1.1
     */
    public int getListeningPort() {
        return listeningPort;
    }

    /**
     * @param listeningPort Local port the DHT service will be listening on
     * @since 1.1
     */
    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    /**
     * @see #setShouldUseRouterBootstrap(boolean)
     * @since 1.1
     */
    public boolean shouldUseRouterBootstrap() {
        return useRouterBootstrap;
    }

    /**
     * @param useRouterBootstrap Indicates whether public bootstrap services will be used
     * @since 1.1
     */
    public void setShouldUseRouterBootstrap(boolean useRouterBootstrap) {
        this.useRouterBootstrap = useRouterBootstrap;
    }

    /**
     * @see #shouldUseIPv6()
     * @since 1.1
     */
    public boolean shouldUseIPv6() {
        return useIPv6;
    }

    /**
     * @param useIPv6 Indicates whether IPv6 interface should be preferred for attaching the local DHT node.
     *                Should not be set to true, if no IPv6 interfaces are available on current system.
     * @since 1.1
     */
    public void setShouldUseIPv6(boolean useIPv6) {
        this.useIPv6 = useIPv6;
    }
}
