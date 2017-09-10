package bt.event;

import java.util.function.Consumer;

/**
 * @since 1.5
 */
public interface EventSource {

    /**
     * @since 1.5
     */
    EventSource onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener);

    /**
     * @since 1.5
     */
    EventSource onPeerConnected(Consumer<PeerConnectedEvent> listener);

    /**
     * @since 1.5
     */
    EventSource onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener);

    /**
     * @since 1.5
     */
    EventSource onPeerBitfieldUpdated(Consumer<PeerBitfieldUpdatedEvent> listener);

    /**
     * @since 1.5
     */
    EventSource onTorrentStarted(Consumer<TorrentStartedEvent> listener);

    /**
     * @since 1.5
     */
    EventSource onTorrentStopped(Consumer<TorrentStoppedEvent> listener);
}
