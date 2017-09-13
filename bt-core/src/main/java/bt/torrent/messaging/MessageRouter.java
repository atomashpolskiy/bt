package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;

/**
 * @since 1.3
 */
public interface MessageRouter {

    /**
     * Route a message to consumers.
     *
     * @since 1.3
     */
    void consume(Message message, MessageContext context);

    /**
     * Request a message from producers.
     *
     * @since 1.3
     */
    void produce(Consumer<Message> messageConsumer, MessageContext context);

    /**
     * Add a messaging agent, that can act as a message consumer and/or producer.
     *
     * @see bt.torrent.annotation.Consumes
     * @see bt.torrent.annotation.Produces
     * @since 1.3
     */
    void registerMessagingAgent(Object agent);

    /**
     * Remove a messaging agent, if it's registered in this message router.
     *
     * @since 1.3
     */
    void unregisterMessagingAgent(Object agent);
}
