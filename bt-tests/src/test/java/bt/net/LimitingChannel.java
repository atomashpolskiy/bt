package bt.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

class LimitingChannel implements ReadableByteChannel {

    private final ReadableByteChannel delegate;
    private final int[] limits;
    private final boolean endWithEOF;
    private int currentLimitIndex;

    static LimitingChannel withEOF(ReadableByteChannel delegate, int[] limits) {
        return new LimitingChannel(delegate, limits, true);
    }

    LimitingChannel(ReadableByteChannel delegate, int[] limits) {
        this(delegate, limits, false);
    }

    /**
     * @param delegate
     * @param limits
     * @param endWithEOF return EOF for {@code limits.length}-th call
     */
    private LimitingChannel(ReadableByteChannel delegate, int[] limits, boolean endWithEOF) {
        this.delegate = delegate;
        this.limits = limits;
        this.endWithEOF = endWithEOF;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int read = 0;
        if (currentLimitIndex < limits.length) {
            int origlim = dst.limit();
            dst.limit(limits[currentLimitIndex]);
            read = delegate.read(dst);
            dst.limit(origlim);
            currentLimitIndex++;
        } else if (endWithEOF) {
            read = -1; // EOF
        }
        return read;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
