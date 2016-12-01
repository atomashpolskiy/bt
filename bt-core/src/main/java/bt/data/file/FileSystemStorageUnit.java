package bt.data.file;

import bt.BtException;
import bt.data.StorageUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

class FileSystemStorageUnit implements StorageUnit {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStorageUnit.class);

    private File parent, file;
    private RandomAccessFile raf;
    private long capacity;

    private volatile boolean closed;

    FileSystemStorageUnit(File root, String path, long capacity) {
        this.file = new File(root, path);
        this.parent = file.getParentFile();
        this.capacity = capacity;
        this.closed = true;
    }

    // TODO: this is temporary fix for verification upon app start
    // should be re-done (probably need additional API to know if storage unit is "empty")
    private boolean init(boolean create) {

        if (closed) {
            if (!parent.exists()) {
                if (create && !parent.mkdirs()) {
                    throw new BtException("Failed to create file storage -- can't create (some of the) directories");
                }
            }

            if (!file.exists()) {
                if (create) {
                    try {
                        if (!file.createNewFile()) {
                            throw new BtException("Failed to create file storage -- " +
                                    "can't create new file: " + file.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        throw new BtException("Failed to create file storage -- unexpected I/O error", e);
                    }
                } else {
                    return false;
                }
            }

            try {
                raf = new RandomAccessFile(file, "rwd");
            } catch (FileNotFoundException e) {
                throw new BtException("Unexpected I/O error", e);
            }

            closed = false;
        }
        return true;
    }

    @Override
    public void readBlock(ByteBuffer buffer, long offset) {

        if (closed) {
            if (!init(false)) {
                return;
            }
        }

        if (offset < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + ")");
        } else if (offset > capacity - buffer.remaining()) {
            throw new BtException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file size: " + capacity);
        }

        try {
            raf.seek(offset);
            raf.getChannel().read(buffer);

        } catch (IOException e) {
            throw new BtException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public byte[] readBlock(long offset, int length) {

        if (closed) {
            if (!init(false)) {
                // TODO: should we return null here? or init this "stub" in constructor?
                return new byte[length];
            }
        }

        if (offset < 0 || length < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + "), length (" + length + ")");
        } else if (offset > capacity - length) {
            throw new BtException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + length + ", file size: " + capacity);
        }

        try {
            raf.seek(offset);
            byte[] block = new byte[length];
            raf.read(block);
            return block;

        } catch (IOException e) {
            throw new BtException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + length + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public void writeBlock(ByteBuffer buffer, long offset) {

        if (closed) {
            init(true);
        }

        if (offset < 0) {
            throw new BtException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new BtException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file size: " + capacity);
        }

        try {
            raf.seek(offset);
            raf.getChannel().write(buffer);

        } catch (IOException e) {
            throw new BtException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public void writeBlock(byte[] block, long offset) {

        if (closed) {
            init(true);
        }

        if (offset < 0) {
            throw new BtException("Negative offset: " + offset);
        } else if (offset > capacity - block.length) {
            throw new BtException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + block.length + ", file size: " + capacity);
        }

        try {
            raf.seek(offset);
            raf.write(block);

        } catch (IOException e) {
            throw new BtException("Failed to write bytes (offset: " + offset +
                    ", block length: " + block.length + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return "(" + capacity + " B) " + file.getPath();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                raf.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close file: " + file.getPath(), e);
            } finally {
                closed = true;
            }
        }
    }
}
