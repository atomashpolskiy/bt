package bt.net;

/**
 * Handles new peer connections.
 *
 * @since 1.0
 */
public interface ConnectionHandler {

    /**
     * Determines whether the connection can be established or should be immediately dropped.
     * Implementations are free (and often expected) to receive and send messages
     * via the provided connection.
     *
     * @param connection Connection with remote peer
     * @return true if it is safe to proceed with establishing the connection,
     *         false if this connection should (is recommended to) be dropped,
     * @since 1.0
     */
    boolean handleConnection(IPeerConnection connection);
}
