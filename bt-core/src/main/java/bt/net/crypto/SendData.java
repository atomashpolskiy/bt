package bt.net.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class SendData {

    private static final int WRITE_ATTEMPTS = 10;

    private final WritableByteChannel channel;

    public SendData(WritableByteChannel channel) {
        this.channel = channel;
    }

    public void execute(ByteBuffer buffer) {
        writeMessageFromBuffer(buffer);
    }

    // TODO: duplicates code in bt.net.DefaultMessageWorker
    private void writeMessageFromBuffer(ByteBuffer buffer) {
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
                            throw new RuntimeException("Interrupted while writing message", e);
                        }
                    } else {
                        throw new RuntimeException("Failed to write message in " + WRITE_ATTEMPTS + " attempts");
                    }
                }
            } while (offset < buffer.limit());
        } catch (IOException e) {
            throw new RuntimeException("Unexpected error when writing message", e);
        }
    }
}
