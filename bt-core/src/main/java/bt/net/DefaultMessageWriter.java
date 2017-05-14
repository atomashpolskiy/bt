package bt.net;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.function.Consumer;

public class DefaultMessageWriter implements Consumer<Message> {

    private static final int WRITE_ATTEMPTS = 10;

    private final WritableByteChannel channel;
    private final MessageHandler<Message> messageHandler;

    private final ByteBuffer buffer;

    public DefaultMessageWriter(WritableByteChannel channel,
                                MessageHandler<Message> messageHandler,
                                int bufferSize) {
        this.channel = channel;
        this.messageHandler = messageHandler;
        this.buffer = ByteBuffer.allocateDirect(bufferSize);

    }

    @Override
    public void accept(Message message) {
        buffer.clear();
        if (!writeToBuffer(message, buffer)) {
            throw new BtException("Insufficient space in buffer for message: " + message);
        }
        buffer.flip();
        writeMessageFromBuffer();
    }

    protected boolean writeToBuffer(Message message, ByteBuffer buffer) {
        return messageHandler.encode(message, buffer);
    }

    private void writeMessageFromBuffer() {
        int offset = buffer.position();
        int written;
        try {
            int k = 0;
            do {
                buffer.position(offset);
                written = channel.write(buffer);
                offset = offset + written;

                if (offset < buffer.limit()) {
                    if (++k <= WRITE_ATTEMPTS) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new BtException("Interrupted while writing message", e);
                        }
                    } else {
                        throw new BtException("Failed to write message in " + WRITE_ATTEMPTS + " attempts");
                    }
                }
            } while (offset < buffer.limit());
        } catch (IOException e) {
            throw new BtException("Unexpected error when writing message", e);
        }
    }
}
