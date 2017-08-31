package bt.net;

import bt.BtException;
import bt.protocol.DecodingContext;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reads and decodes peer messages from a byte channel.
 *
 * Note that this class is not a part of the public API and is subject to change.
 *
 * @since 1.2
 */
class DefaultMessageReader implements Supplier<Message> {

    private final ReadableByteChannel channel;
    private final MessageHandler<Message> messageHandler;

    private final Peer peer;
    private DecodingContext context;

    private final ByteBuffer buffer;
    private final ByteBuffer readOnlyBuffer;

    private int dataOffset;

    /**
     * Create a message reader with a private buffer
     *
     * @param peer Remote peer
     * @param channel Readable byte channel
     * @param messageHandler Message decoder
     * @param bufferSize Size of the internal buffer, that will be used to store
     *                   partially received messages until the remaining data arrives
     * @since 1.2
     */
    public DefaultMessageReader(Peer peer,
                                ReadableByteChannel channel,
                                MessageHandler<Message> messageHandler,
                                int bufferSize) {
        this(peer, channel, messageHandler, ByteBuffer.allocateDirect(bufferSize));
    }

    /**
     * Create a message reader with the provided buffer.
     * Data that is between position 0 and current buffer's position will be decoded first.
     *
     * @param peer Remote peer
     * @param channel Readable byte channel
     * @param messageHandler Message decoder
     * @param buffer Buffer, that will be used to store
     *               partially received messages until the remaining data arrives
     * @since 1.2
     */
    public DefaultMessageReader(Peer peer,
                                ReadableByteChannel channel,
                                MessageHandler<Message> messageHandler,
                                ByteBuffer buffer) {
        this.peer = peer;
        this.channel = channel;
        this.messageHandler = messageHandler;
        this.context = createDecodingContext(peer);
        this.buffer = buffer;
        this.readOnlyBuffer = buffer.asReadOnlyBuffer();
        this.dataOffset = 0;
    }

    @Override
    public Message get() {
        Message message = readMessageFromBuffer();
        if (message == null) {
            if (!buffer.hasRemaining()) {
                compactBuffer(buffer, dataOffset);
                dataOffset = 0;
            }
            int read;
            try {
                read = readToBuffer(channel, buffer);
            } catch (IOException e) {
                throw new BtException("Unexpected error when reading message", e);
            }
            if (read == -1) {
                throw new BtException("Reached end of stream");
            } else if (read > 0) {
                message = readMessageFromBuffer();
                if (message == null && !buffer.hasRemaining()) {
                    compactBuffer(buffer, dataOffset);
                    dataOffset = 0;
                }
            }
        }
        return message;
    }

    protected int readToBuffer(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        return channel.read(buffer);
    }

    private Message readMessageFromBuffer() {
        int dataEndsAtIndex = buffer.position();
        if (dataEndsAtIndex <= dataOffset) {
            return null;
        }

        Message message = null;

        readOnlyBuffer.limit(readOnlyBuffer.capacity());
        readOnlyBuffer.position(dataOffset);
        readOnlyBuffer.limit(dataEndsAtIndex);

        int consumed = messageHandler.decode(context, readOnlyBuffer);
        if (consumed > 0) {
            if (consumed > dataEndsAtIndex - dataOffset) {
                throw new BtException("Unexpected amount of bytes consumed: " + consumed);
            }
            dataOffset += consumed;
            message = Objects.requireNonNull(context.getMessage());
            context = createDecodingContext(peer);
        }
        return message;
    }

    private static void compactBuffer(ByteBuffer buffer, int offset) {
        buffer.limit(buffer.position());
        buffer.position(offset);
        buffer.compact();
        buffer.limit(buffer.capacity());
    }

    private DecodingContext createDecodingContext(Peer peer) {
        return new DecodingContext(peer);
    }
}
