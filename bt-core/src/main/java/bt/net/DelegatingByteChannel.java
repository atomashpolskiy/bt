package bt.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;

public class DelegatingByteChannel extends AbstractSelectableChannel implements ByteChannel {

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
        return delegate.write(src);
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
