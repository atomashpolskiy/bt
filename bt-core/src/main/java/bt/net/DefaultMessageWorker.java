package bt.net;

import bt.BtException;
import bt.protocol.DecodingContext;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Objects;

class DefaultMessageWorker {

    private static final int WRITE_ATTEMPTS = 10;

    private final ByteChannel channel;
    private final MessageHandler<Message> messageHandler;

    private final Peer peer;
    private DecodingContext context;

    private final ByteBuffer bufferOut;
    private final ByteBuffer bufferIn;
    private final ByteBuffer bufferInReadOnly;

    private int bufferInDataOffset;

    DefaultMessageWorker(Peer peer,
                         ByteChannel channel,
                         MessageHandler<Message> messageHandler,
                         int bufferSize) {
        this.channel = channel;
        this.messageHandler = messageHandler;
        this.peer = peer;
        this.context = createDecodingContext(peer);

        this.bufferOut = ByteBuffer.allocateDirect(bufferSize);
        this.bufferIn = ByteBuffer.allocateDirect(bufferSize);
        this.bufferInReadOnly = bufferIn.asReadOnlyBuffer();
        this.bufferInDataOffset = bufferIn.position();
    }

    void writeMessage(Message message) {
        bufferOut.clear();
        if (!messageHandler.encode(message, bufferOut)) {
            throw new BtException("Insufficient space in buffer for message: " + message);
        }
        bufferOut.flip();
        writeMessageFromBuffer();
    }

    private void writeMessageFromBuffer() {
        int offset = bufferOut.position();
        int written;
        try {
            int k = 0;
            do {
                bufferOut.position(offset);
                written = channel.write(bufferOut);
                offset = offset + written;

                if (offset < bufferOut.limit()) {
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
            } while (offset < bufferOut.limit());
        } catch (IOException e) {
            throw new BtException("Unexpected error when writing message", e);
        }
    }

    Message readMessage() {
        Message message = readMessageFromBuffer();
        if (message == null) {
            if (!bufferIn.hasRemaining()) {
                compactBuffer(bufferIn, bufferInDataOffset);
                bufferInDataOffset = 0;
            }
            int read;
            try {
                read = channel.read(bufferIn);
            } catch (IOException e) {
                throw new BtException("Unexpected error when reading message", e);
            }
            if (read > 0) {
                message = readMessageFromBuffer();
                if (message == null && !bufferIn.hasRemaining()) {
                    compactBuffer(bufferIn, bufferInDataOffset);
                    bufferInDataOffset = 0;
                }
            }
        }
        return message;
    }

    private Message readMessageFromBuffer() {
        int dataEndsAtIndex = bufferIn.position();
        if (dataEndsAtIndex <= bufferInDataOffset) {
            return null;
        }

        Message message = null;

        bufferInReadOnly.limit(bufferInReadOnly.capacity());
        bufferInReadOnly.position(bufferInDataOffset);
        bufferInReadOnly.limit(dataEndsAtIndex);

        int consumed = messageHandler.decode(context, bufferInReadOnly);
        if (consumed > 0) {
            if (consumed > dataEndsAtIndex - bufferInDataOffset) {
                throw new BtException("Unexpected amount of bytes consumed: " + consumed);
            }
            bufferInDataOffset += consumed;
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
