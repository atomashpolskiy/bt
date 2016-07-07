package bt.protocol.ext;

import bt.protocol.MessageHandler;

public interface ExtendedMessageHandler<T extends ExtendedMessage> extends MessageHandler<T> {

    Class<T> getMessageType();
}
