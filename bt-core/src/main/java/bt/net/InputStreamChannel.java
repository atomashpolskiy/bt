package bt.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;

public class InputStreamChannel extends AbstractSelectableChannel implements ReadableByteChannel {

    private static final int MAX_TRANSFER_SIZE = 8192;

    private final InputStream in;
    private byte[] buf;

    public InputStreamChannel(InputStream in) {
        // can't do much about it
        super(null);
        this.in = in;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int totalRead = 0;
        int read = 0;
        while (dst.hasRemaining()) {
            if (in.available() <= 0) {
                break;
            }
            int remaining = Math.min(dst.remaining(), MAX_TRANSFER_SIZE);
            int bytesToRead = Math.min(remaining, in.available());
            if (buf == null || buf.length < bytesToRead) {
                buf = new byte[bytesToRead];
            }
            try {
                begin(); // prepare for interrupt
                read = in.read(buf, 0, bytesToRead);
            } finally {
                end(read > 0); // catch interrupt
            }
            if (read < 0) {
                break;
            } else {
                totalRead += read;
            }
            dst.put(buf, 0, read);

        }
        if ((read < 0) && (totalRead == 0)) {
            return -1;
        }
        return totalRead;
    }

    @Override
    public int validOps() {
        return SelectionKey.OP_READ;
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        in.close();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        // do nothing
    }
}
