package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;

/**
 * Message agent, that is interested in producing messages
 *
 * @see bt.torrent.annotation.Produces
 * @since 1.0
 */
public interface MessageProducer {

    /**
     * Produce a message to the remote peer, if possible
     *
     * @since 1.0
     */
    void produce(Consumer<Message> messageConsumer, MessageContext context);
}
