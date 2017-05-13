package bt.net.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class SendData {

    private final WritableByteChannel channel;

    public SendData(WritableByteChannel channel) {
        this.channel = channel;
    }

    public void execute(ByteBuffer buffer) {
        try {
            channel.write(buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
