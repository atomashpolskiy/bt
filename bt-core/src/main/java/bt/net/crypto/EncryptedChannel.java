package bt.net.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

class EncryptedChannel implements ByteChannel {

    private final ByteChannel delegate;
    private final MSECipher cipher;

    EncryptedChannel(ByteChannel delegate, MSECipher cipher) {
        this.delegate = delegate;
        this.cipher = cipher;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int read = 0;
        if (dst.hasRemaining()) {
            int position = dst.position();
            int limit = dst.limit();
            read = delegate.read(dst);
            if (read > 0) {
                dst.limit(dst.position());
                dst.position(position);
                byte[] bytes = new byte[dst.remaining()];
                dst.get(bytes);
                dst.limit(limit);
                dst.position(position);
                try {
                    bytes = cipher.getDecryptionCipher().update(bytes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                dst.put(bytes);
            }
        }
        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int written = 0;
        if (src.hasRemaining()) {
            int position = src.position();
            byte[] bytes = new byte[src.remaining()];
            src.get(bytes);
            src.position(position);
            try {
                bytes = cipher.getEncryptionCipher().update(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            src.put(bytes);
            src.position(position);
            written = delegate.write(src);
        }
        return written;
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
