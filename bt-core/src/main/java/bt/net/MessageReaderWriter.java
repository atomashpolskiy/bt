package bt.net;

import bt.protocol.Message;

import java.util.Optional;

interface MessageReaderWriter {

    Optional<Message> readMessage();

    void writeMessage(Message message);
}
