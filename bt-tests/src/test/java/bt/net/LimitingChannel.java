package bt.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

class LimitingChannel implements ReadableByteChannel {

    private final ReadableByteChannel delegate;
    private final int[] limits;
    private final boolean endWithEOF;
    private int currentLimitIndex;

    /**
     * Create a channel that will return -1 (EOF) when the number of reads exceeds the amount of limits
     *
     * @param delegate Data channel
     * @param limits List of buffer limits to set for i-th read from this channel
     */
    static LimitingChannel withEOF(ReadableByteChannel delegate, int[] limits) {
        return new LimitingChannel(delegate, limits, true);
    }

    /**
     * Create a channel that will continuously return 0 when the number of reads exceeds the amount of limits
     *
     * @param delegate Data channel
     * @param limits List of buffer limits to set for i-th read from this channel
     */
    LimitingChannel(ReadableByteChannel delegate, int[] limits) {
        this(delegate, limits, false);
    }

    /**
     * @param delegate Data channel
     * @param limits List of buffer limits to set for i-th read from this channel
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
