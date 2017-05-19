package bt.net;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.function.Consumer;

/**
 * Encodes and writes messages to a byte channel.
 *
 * Note that this class is not a part of the public API and is subject to change.
 *
 * @since 1.2
 */
class DefaultMessageWriter implements Consumer<Message> {

    private static final int WRITE_ATTEMPTS = 10;

    private final WritableByteChannel channel;
    private final MessageHandler<Message> messageHandler;

    private final ByteBuffer buffer;

    /**
     * Create a message writer with a private buffer
     *
     * @param channel Writable byte channel
     * @param messageHandler Message encoder
     * @param bufferSize Size of the internal buffer, that will be used to store encoded but not yet sent messages.
     * @since 1.2
     */
    public DefaultMessageWriter(WritableByteChannel channel,
                                MessageHandler<Message> messageHandler,
                                int bufferSize) {
        this(channel, messageHandler, ByteBuffer.allocateDirect(bufferSize));
    }

    /**
     * Create a message writer with the provided buffer
     *
     * @param channel Writable byte channel
     * @param messageHandler Message encoder
     * @param buffer Buffer, that will be used to store encoded but not yet sent messages.
     * @since 1.2
     */
    public DefaultMessageWriter(WritableByteChannel channel,
                                MessageHandler<Message> messageHandler,
                                ByteBuffer buffer) {
        this.channel = channel;
        this.messageHandler = messageHandler;
        this.buffer = buffer;
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
