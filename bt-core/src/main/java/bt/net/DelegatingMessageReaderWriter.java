package bt.net;

import bt.protocol.Message;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DelegatingMessageReaderWriter implements MessageReaderWriter {

    private final Supplier<Message> reader;
    private final Consumer<Message> writer;

    public DelegatingMessageReaderWriter(Supplier<Message> reader, Consumer<Message> writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public Optional<Message> readMessage() {
        return Optional.ofNullable(reader.get());
    }

    @Override
    public void writeMessage(Message message) {
        writer.accept(message);
    }
}
