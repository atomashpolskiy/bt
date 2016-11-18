package bt.net;

import bt.protocol.Message;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Provides access to messaging with remote peers.
 *
 * @since 1.0
 */
public interface IMessageDispatcher {

    /**
     * Add a message consumer to receive messages from a remote peer.
     *
     * @param sender Remote peer, whose messages should be relayed to the consumer
     * @param messageConsumer Message consumer
     * @since 1.0
     */
    void addMessageConsumer(Peer sender, Consumer<Message> messageConsumer);

    /**
     * Add a message supplier to send messages to a remote peer.
     *
     * @param recipient Remote peer, to whom the supplied messages should be sent
     * @param messageSupplier Message supplier
     * @since 1.0
     */
    void addMessageSupplier(Peer recipient, Supplier<Message> messageSupplier);
}
