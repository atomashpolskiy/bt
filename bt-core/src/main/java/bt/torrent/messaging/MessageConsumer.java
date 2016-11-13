package bt.torrent.messaging;

import bt.protocol.Message;

public interface MessageConsumer<T extends Message> {

    Class<T> getConsumedType();

    void consume(T message, MessageContext context);
}
