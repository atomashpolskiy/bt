package bt.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;

public class DelegatingByteChannel extends AbstractSelectableChannel implements ByteChannel {

    private static final int WRITE_ATTEMPTS = 10;

    private final ByteChannel delegate;

    public DelegatingByteChannel(ByteChannel delegate) {
        // can't do much about it
        super(null);
        this.delegate = delegate;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return writeMessageFromBuffer(src);
    }

    // TODO: duplicates code in bt.net.DefaultMessageWorker
    private int writeMessageFromBuffer(ByteBuffer buffer) {
        int offset = buffer.position();
        int written;
        try {
            int k = 0;
            do {
                buffer.position(offset);
                written = delegate.write(buffer);
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
        return written;
    }

    @Override
    public int validOps() {
        return (SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        delegate.close();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        // do nothing
    }
}
