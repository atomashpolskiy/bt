package bt.net;

import bt.protocol.Message;

import java.util.Optional;

public interface MessageReaderWriter {

    Optional<Message> readMessage();

    void writeMessage(Message message);
}
