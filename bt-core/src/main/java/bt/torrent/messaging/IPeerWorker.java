package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Instances of this class are responsible for providing a messaging interface
 * with one particular peer within a torrent processing session.
 *
 * @since 1.0
 */
public interface IPeerWorker extends Consumer<Message>, Supplier<Message> {

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    ConnectionState getConnectionState();
}
