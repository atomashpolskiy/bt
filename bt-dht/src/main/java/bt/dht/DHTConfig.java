package bt.dht;

public class DHTConfig {

    private int listeningPort;
    private boolean useRouterBootstrap;
    private boolean useIPv6;

    public DHTConfig() {
        this.listeningPort = 49001;
        this.useRouterBootstrap = false;
        this.useIPv6 = false;
    }

    public int getListeningPort() {
        return listeningPort;
    }

    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    public boolean shouldUseRouterBootstrap() {
        return useRouterBootstrap;
    }

    public void setShouldUseRouterBootstrap(boolean useRouterBootstrap) {
        this.useRouterBootstrap = useRouterBootstrap;
    }

    public boolean shouldUseIPv6() {
        return useIPv6;
    }

    public void setShouldUseIPv6(boolean useIPv6) {
        this.useIPv6 = useIPv6;
    }
}
