package bt.net;

import bt.protocol.Message;

import java.io.IOException;
import java.util.Optional;

class DelegatingPeerConnectionMessageWorker implements PeerConnectionMessageWorker {

    private final MessageReader reader;
    private final MessageWriter writer;

    public DelegatingPeerConnectionMessageWorker(MessageReader reader, MessageWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public Optional<Message> readMessage() throws IOException {
        return Optional.ofNullable(reader.readMessage());
    }

    @Override
    public void writeMessage(Message message) throws IOException {
        writer.writeMessage(message);
    }
}
