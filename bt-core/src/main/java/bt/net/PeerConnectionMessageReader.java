package bt.net;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.MessageContext;
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

    private Supplier<MessageContext> newContextSupplier;
    private MessageContext context;

    PeerConnectionMessageReader(MessageHandler<Message> messageHandler, ReadableByteChannel channel,
                                Supplier<MessageContext> newContextSupplier, int bufferSize) {

        this.messageHandler = messageHandler;
        this.channel = channel;

        buffer = ByteBuffer.allocateDirect(bufferSize);
        readOnlyBuffer = buffer.asReadOnlyBuffer();
        dataStartsAtIndex = buffer.position();

        this.newContextSupplier = newContextSupplier;
        context = newContextSupplier.get();

    }

    Message readMessage() {

        Message message = readMessageFromBuffer();
        if (message == null) {
            int read;
            try {
                read = channel.read(buffer);
            } catch (IOException e) {
                throw new BtException("Unexpected error when reading message", e);
            }
            if (read > 0) {
                message = readMessageFromBuffer();
                if (message == null && !buffer.hasRemaining()) {
                    buffer.position(dataStartsAtIndex);
                    buffer.compact();
                    dataStartsAtIndex = 0;
                }
            }
        }
        return message;
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
