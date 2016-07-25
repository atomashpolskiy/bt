package bt.net;

import bt.BtException;
import bt.Constants;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

class PeerConnectionMessageWriter {

    private static final int BUFFER_CAPACITY = Constants.MAX_BLOCK_SIZE * 2;
    private static final int WRITE_ATTEMPTS = 10;

    private MessageHandler<Message> messageHandler;
    private SocketChannel channel;
    private ByteBuffer buffer;

    PeerConnectionMessageWriter(MessageHandler<Message> messageHandler, SocketChannel channel) {
        this.messageHandler = messageHandler;
        this.channel = channel;
        buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
    }

    void writeMessage(Message message) {

        int begin = buffer.position();
        if (!messageHandler.encode(message, buffer)) {
            buffer.position(begin);
            buffer.compact();
            begin = 0;
            if (!messageHandler.encode(message, buffer)) {
                throw new BtException("Insufficient space in buffer for message: " + message);
            }
        }

        int end = buffer.position();
        buffer.position(begin);
        buffer.limit(end);

        try {
            if (channel.isBlocking()) {
                int written = channel.write(buffer);
                if (begin + written < end) {
                    throw new BtException("Failed to write the whole message (in blocking mode)");
                }
            } else {
                int written;
                int k = 0;
                do {
                    buffer.position(begin);
                    written = channel.write(buffer);
                    k++;
                    if (k > 1) {
                        if (k <= WRITE_ATTEMPTS) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new BtException("Interrupted while writing message", e);
                            }
                        } else {
                            throw new BtException("Failed to write message in " + WRITE_ATTEMPTS + " attempts");
                        }
                    }
                } while ((begin = begin + written) < end);
            }
        } catch (IOException e) {
            throw new BtException("Unexpected error when writing message", e);
        }

        buffer.limit(buffer.capacity());
        buffer.position(end);
    }
}
