package bt.event;

import java.util.function.Consumer;

public interface EventSource {

    EventSource onPeerDiscovered(Consumer<PeerDiscoveredEvent> listener);

    EventSource onPeerConnected(Consumer<PeerConnectedEvent> listener);

    EventSource onPeerDisconnected(Consumer<PeerDisconnectedEvent> listener);
}
