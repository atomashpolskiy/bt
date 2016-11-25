package bt.peerexchange;

import java.time.Duration;

public class PeerExchangeConfig {

    private Duration minMessageInterval;
    private int minEventsPerMessage;
    private int maxEventsPerMessage;

    public PeerExchangeConfig() {
        this.minMessageInterval = Duration.ofMinutes(1);
        this.minEventsPerMessage = 10;
        this.maxEventsPerMessage = 50;
    }

    /**
     * @param minMessageInterval Minimal interval between sending peer exchange messages to a peer
     * @since 1.0
     */
    public void setMinMessageInterval(Duration minMessageInterval) {
        this.minMessageInterval = minMessageInterval;
    }

    /**
     * @since 1.0
     */
    public Duration getMinMessageInterval() {
        return minMessageInterval;
    }

    /**
     * @param minEventsPerMessage Minimal amount of events in a peer exchange message
     * @since 1.0
     */
    public void setMinEventsPerMessage(int minEventsPerMessage) {
        this.minEventsPerMessage = minEventsPerMessage;
    }

    /**
     * @since 1.0
     */
    public int getMinEventsPerMessage() {
        return minEventsPerMessage;
    }

    /**
     * @param maxEventsPerMessage Maximal amount of events in a peer exchange message
     * @since 1.0
     */
    public void setMaxEventsPerMessage(int maxEventsPerMessage) {
        this.maxEventsPerMessage = maxEventsPerMessage;
    }

    /**
     * @since 1.0
     */
    public int getMaxEventsPerMessage() {
        return maxEventsPerMessage;
    }
}
