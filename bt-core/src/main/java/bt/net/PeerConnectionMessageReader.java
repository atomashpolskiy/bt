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

class PeerConnectionMessageReader {

    private MessageHandler<Message> messageHandler;
    private ReadableByteChannel channel;
    private ByteBuffer buffer, readOnlyBuffer;
    private int dataStartsAtIndex;

    private Supplier<DecodingContext> newContextSupplier;
    private DecodingContext context;

    PeerConnectionMessageReader(MessageHandler<Message> messageHandler,
                                ReadableByteChannel channel,
                                Supplier<DecodingContext> newContextSupplier,
                                int bufferSize) {
        this.messageHandler = messageHandler;
        this.channel = channel;

        this.buffer = ByteBuffer.allocateDirect(bufferSize);
        this.readOnlyBuffer = buffer.asReadOnlyBuffer();
        this.dataStartsAtIndex = buffer.position();

        this.newContextSupplier = newContextSupplier;
        this.context = newContextSupplier.get();
    }

    Message readMessage() {

        Message message = readMessageFromBuffer();
        if (message == null) {
            if (!buffer.hasRemaining()) {
                compactBuffer();
            }
            int read;
            try {
                read = channel.read(buffer);
            } catch (IOException e) {
                throw new BtException("Unexpected error when reading message", e);
            }
            if (read > 0) {
                message = readMessageFromBuffer();
                if (message == null && !buffer.hasRemaining()) {
                    compactBuffer();
                }
            }
        }
        return message;
    }

    private void compactBuffer() {
        int dataEndsAtIndex = buffer.position();
        buffer.position(dataStartsAtIndex);
        buffer.compact();
        buffer.position(dataEndsAtIndex - dataStartsAtIndex);
        dataStartsAtIndex = 0;
    }

    private Message readMessageFromBuffer() {

        int dataEndsAtIndex = buffer.position();
        if (dataEndsAtIndex <= dataStartsAtIndex) {
            return null;
        }

        Message message = null;

        readOnlyBuffer.position(dataStartsAtIndex);
        readOnlyBuffer.limit(dataEndsAtIndex);

        int consumed = messageHandler.decode(context, readOnlyBuffer);
        if (consumed > 0) {
            if (dataEndsAtIndex - consumed < dataStartsAtIndex) {
                throw new BtException("Unexpected amount of bytes consumed: " + consumed);
            }
            dataStartsAtIndex += consumed;
            message = Objects.requireNonNull(context.getMessage());
            context = newContextSupplier.get();
        }
        return message;
    }
}
