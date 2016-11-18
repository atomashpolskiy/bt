package bt.torrent.messaging;

import bt.protocol.Message;

/**
 * Message agent, that is interested in receiving messages of type T
 *
 * @see bt.torrent.annotation.Consumes
 * @param <T> Message type
 * @since 1.0
 */
public interface MessageConsumer<T extends Message> {

    /**
     * @return Message type, that this consumer is interested in
     * @since 1.0
     */
    Class<T> getConsumedType();

    /**
     * @since 1.0
     */
    void consume(T message, MessageContext context);
}
