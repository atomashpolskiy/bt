package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;

public interface MessageProducer {

    void produce(Consumer<Message> messageConsumer, MessageContext context);
}
