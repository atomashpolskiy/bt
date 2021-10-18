package bt.torrent.messaging;

/**
 * An interface for state tracking objects on a per connection basis. Internally, this is stored in a {@link java.util.Map} in
 * a connection's {@link ConnectionState} to ensure that it is unique per connection.
 * <p>
 * The class implementing this interface must have a default constructor available. If the connection does not yet have
 * this state object, it is build using the default constructor via reflection.
 */
public interface ExtensionConnectionState<V> {
}
