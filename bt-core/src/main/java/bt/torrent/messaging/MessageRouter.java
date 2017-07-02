package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;

public interface MessageRouter {

    void consume(Message message, MessageContext context);

    void produce(Consumer<Message> messageConsumer, MessageContext context);

    void registerMessagingAgent(Object agent);

    void unregisterMessagingAgent(Object agent);
}
