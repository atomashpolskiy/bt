package bt.net.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.Arrays;

class DataReader {

    private final ReadableByteChannel channel;
    private final Duration timeout;

    DataReader(ReadableByteChannel channel, Duration timeout) {
        this.channel = channel;
        this.timeout = timeout;
    }

    int timedSync(ByteBuffer buf, int min, int limit, byte[] pattern) throws IOException {
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
        do {
            read = channel.read(buf);
            if (read < 0) {
                throw new RuntimeException("Received EOF, total bytes read: " + readTotal + ", expected: " + min + ".." + limit);
            } else if (read > 0) {
                readTotal += read;
                if (readTotal > limit) {
                    throw new IllegalStateException("More than " + limit + " bytes was received: " + readTotal);
                }
                if (!found) {
                    int pos = buf.position();
                    buf.position(searchpos);
                    buf.limit(searchpos + read);
                    if (buf.remaining() >= pattern.length) {
                        if (searchPattern(buf, pattern)) {
                            found = true;
                            matchpos = buf.position();
                        } else {
                            searchpos = pos - pattern.length + 1;
                        }
                        buf.limit(origlim);
                        buf.position(pos);
                    }
                }
            }
            if (readTotal >= min) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for data", e);
            }
        } while (System.currentTimeMillis() - t1 <= timeoutMillis);

        if (readTotal < min) {
            throw new IllegalStateException("Less than " + min + " bytes was received: " + readTotal);
        } else if (!found) {
            throw new IllegalStateException("Failed to synchronize: expected " + min + ".." + limit + ", received " + readTotal);
        }

        buf.position(matchpos);
        return readTotal;
    }

    /**
     * Searches for the first pattern match in the provided buffer.
     * Buffer's position might change in the following ways after this methods returns:
     * - if a pattern was not found, then the buffer's position will not change
     * - if a pattern was found, then the buffer's position will be right after the last index
     *   of the matching subrange (probably equal to buffer's limit)
     *
     * @return true if pattern was found in the provided buffer
     */
    private boolean searchPattern(ByteBuffer buf, byte[] pattern) {
        if (buf.remaining() < pattern.length) {
            return false;
        }

        int pos = buf.position();

        int len = pattern.length;
        int p = 31;
        int mult0 = 1;
        for (int i = 0; i < len - 1; i++) {
            mult0 *= p;
        }
        int hash = 0;
        for (int i = 0; i < len - 1; i++) {
            hash += pattern[i];
            hash *= p;
        }
        hash += pattern[len - 1];

        int bufhash = 0;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len - 1; i++) {
            byte b = buf.get();
            bytes[i] = b;
            bufhash += b;
            bufhash *= p;
        }
        bufhash += buf.get();

        boolean found = false;
        do {
            if (bufhash == hash && Arrays.equals(pattern, bytes)) {
                found = true;
                break;
            }
            byte next = buf.get();
            bufhash -= (bytes[0] * mult0);
            bufhash *= p;
            bufhash += next;
            System.arraycopy(bytes, 1, bytes, 0, len - 1);
            bytes[len - 1] = next;
        } while (buf.hasRemaining());

        if (!found) {
            buf.position(pos);
        }
        return found;
    }

    int timedRead(ByteBuffer buf, int min, int limit) throws IOException {
        return read(channel, buf, timeout, min, limit);
    }

    private int read(ReadableByteChannel channel, ByteBuffer buf, Duration timeout, int min, int limit) throws IOException {
        checkArguments(buf, min, limit);

        long t1 = System.currentTimeMillis();
        int readTotal = 0;
        int read;
        long timeoutMillis = timeout.toMillis();
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
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for data", e);
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
