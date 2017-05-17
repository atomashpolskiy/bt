package bt.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;

public class ByteChannelReader {

    private final ReadableByteChannel channel;
    private final Duration timeout;
    private final Duration waitBetweenReads;

    public ByteChannelReader(ReadableByteChannel channel, Duration timeout, Duration waitBetweenReads) {
        this.channel = channel;
        this.timeout = timeout;
        this.waitBetweenReads = waitBetweenReads;
    }

    public int timedSync(ByteBuffer buf, int min, int limit, byte[] pattern) throws IOException {
        checkArguments(buf, min, limit);
        if (pattern.length == 0) {
            throw new IllegalArgumentException("Empty pattern");
        }

        int searchpos = buf.position(), origlim = buf.limit();
        boolean found = false;
        int matchpos = -1;
        long t1 = System.currentTimeMillis();
        int readTotal = 0;
        int read;
        long timeoutMillis = timeout.toMillis();
        long waitBetweenReadsMillis = waitBetweenReads.toMillis();
        do {
            read = channel.read(buf);
            if (read < 0) {
                throw new RuntimeException("Received EOF, total bytes read: " + readTotal + ", expected: " + min + ".." + limit);
            } else if (read > 0) {
                readTotal += read;
                if (readTotal > limit) {
                    throw new IllegalStateException("More than " + limit + " bytes received: " + readTotal);
                }
                if (!found) {
                    int pos = buf.position();
                    buf.flip();
                    buf.position(searchpos);
                    if (buf.remaining() >= pattern.length) {
                        if (Buffers.searchPattern(buf, pattern)) {
                            found = true;
                            matchpos = buf.position();
                        } else {
                            searchpos = pos - pattern.length + 1;
                        }
                    }
                    buf.limit(origlim);
                    buf.position(pos);
                }
            }
            if (found && readTotal >= min) {
                break;
            }
            if (waitBetweenReadsMillis > 0) {
                try {
                    Thread.sleep(waitBetweenReadsMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for data", e);
                }
            }
        } while (System.currentTimeMillis() - t1 <= timeoutMillis);

        if (readTotal < min) {
            throw new IllegalStateException("Less than " + min + " bytes received: " + readTotal);
        } else if (!found) {
            throw new IllegalStateException("Failed to synchronize: expected " + min + ".." + limit + ", received " + readTotal);
        }

        buf.position(matchpos);
        return readTotal;
    }

    public int timedRead(ByteBuffer buf, int min, int limit) throws IOException {
        return read(channel, buf, timeout, min, limit);
    }

    private int read(ReadableByteChannel channel, ByteBuffer buf, Duration timeout, int min, int limit) throws IOException {
        checkArguments(buf, min, limit);

        long t1 = System.currentTimeMillis();
        int readTotal = 0;
        int read;
        long timeoutMillis = timeout.toMillis();
        long waitBetweenReadsMillis = waitBetweenReads.toMillis();
        do {
            read = channel.read(buf);
            if (read < 0) {
                throw new RuntimeException("Received EOF, total bytes read: " + readTotal + ", expected: " + min + ".." + limit);
            } else {
                readTotal += read;
            }
            if (readTotal > limit) {
                throw new IllegalStateException("More than " + limit + " bytes received: " + readTotal);
            } else if (readTotal >= min) {
                break;
            }
            if (waitBetweenReadsMillis > 0) {
                try {
                    Thread.sleep(waitBetweenReadsMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for data", e);
                }
            }
        } while (System.currentTimeMillis() - t1 <= timeoutMillis);

        if (readTotal < min) {
            throw new IllegalStateException("Less than " + min + " bytes received: " + readTotal);
        }

        return readTotal;
    }

    private void checkArguments(ByteBuffer buf, int min, int limit) {
        if (min < 0 || limit < 0 || limit < min) {
            throw new IllegalArgumentException("Illegal arguments: min (" + min + "), limit (" + limit + ")");
        }
        if (buf.remaining() < limit) {
            throw new IllegalArgumentException("Insufficient space in buffer: " + buf.remaining() + ", required: " + limit);
        }
    }
}
