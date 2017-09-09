package bt.net;

import bt.protocol.Message;

import java.io.IOException;
import java.util.Optional;

interface PeerConnectionMessageWorker {

    Optional<Message> readMessage() throws IOException;

    void writeMessage(Message message) throws IOException;
}
