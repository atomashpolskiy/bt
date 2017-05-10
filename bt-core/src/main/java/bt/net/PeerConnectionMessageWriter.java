package bt.net;

import bt.BtException;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

class PeerConnectionMessageWriter {

    private static final int WRITE_ATTEMPTS = 10;

    private MessageHandler<Message> messageHandler;
    private WritableByteChannel channel;
    private ByteBuffer buffer;

    PeerConnectionMessageWriter(MessageHandler<Message> messageHandler,
                                WritableByteChannel channel,
                                int bufferSize) {
        this.messageHandler = messageHandler;
        this.channel = channel;
        this.buffer = ByteBuffer.allocateDirect(bufferSize);
    }

    void writeMessage(Message message) {

        int begin = buffer.position();
        if (!messageHandler.encode(message, buffer)) {
            buffer.position(begin);
            buffer.clear();
            begin = buffer.position();
            if (!messageHandler.encode(message, buffer)) {
                throw new BtException("Insufficient space in buffer for message: " + message);
            }
        }

        int end = buffer.position();
        buffer.position(begin);
        buffer.limit(end);

        try {
            int written;
            int k = 0;
            do {
                buffer.position(begin);
                written = channel.write(buffer);
                begin = begin + written;

                if (begin < end) {
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
            } while (begin < end);
        } catch (IOException e) {
            throw new BtException("Unexpected error when writing message", e);
        }

        buffer.limit(buffer.capacity());
        buffer.position(end);
    }
}
