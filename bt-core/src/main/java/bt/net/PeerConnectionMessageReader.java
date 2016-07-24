package bt.net;

import bt.BtException;
import bt.Constants;
import bt.protocol.Message;
import bt.protocol.MessageContext;
import bt.protocol.Protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;
import java.util.function.Supplier;

class PeerConnectionMessageReader {

    private static final int BUFFER_CAPACITY = Constants.MAX_BLOCK_SIZE * 2;

    private Protocol protocol;
    private ReadableByteChannel channel;
    private ByteBuffer buffer, readOnlyBuffer;
    private int dataStartsAtIndex;

    private Supplier<MessageContext> newContextSupplier;
    private MessageContext context;

    PeerConnectionMessageReader(Protocol protocol, ReadableByteChannel channel, Supplier<MessageContext> newContextSupplier) {

        this.protocol = protocol;
        this.channel = channel;

        buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
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

        int consumed = protocol.fromByteArray(context, readOnlyBuffer);
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
