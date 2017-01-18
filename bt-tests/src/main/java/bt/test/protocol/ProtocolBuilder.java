package bt.test.protocol;

import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

interface ProtocolBuilder {

    ProtocolBuilder addMessageHandler(Integer messageTypeId, MessageHandler<?> handler);

    MessageHandler<Message> build();
}
