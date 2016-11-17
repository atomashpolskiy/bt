package bt.data;

import java.io.Closeable;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Storage for a single torrent file
 *
 * @since 1.0
 */
public interface StorageUnit extends Closeable {

    /**
     * Read a block of data into the provided buffer, starting with a given offset.
     * Number of bytes to be read is determined by {@link Buffer#remaining()}.
     * Hence, storage must throw an exception
     * if offset > ({@link #capacity()} - buffer.remaining())
     *
     * @param buffer Buffer to read bytes into. Value returned by its' remaining() method determines
     *               the total number of bytes to read
     * @param offset Index to starting reading from (0-based)
     *
     * @since 1.0
     */
    void readBlock(ByteBuffer buffer, long offset);

    /**
     * Read a block of data, starting with a given offset.
     * Storage must throw an exception
     * if offset > ({@link #capacity()} - length)
     *
     * @param offset Index to starting reading from (0-based)
     * @param length Total number of bytes to read
     *
     * @since 1.0
     */
    byte[] readBlock(long offset, int length);

    /**
     * Write a block of data from the provided buffer to this storage, starting with a given offset.
     * Number of bytes to be written is determined by {@link Buffer#remaining()}.
     * Hence, storage must throw an exception
     * if offset > ({@link #capacity()} - buffer.remaining())
     *
     * @param buffer Buffer containing the block of data to write to this storage.
     *               Value returned by its' remaining() method determines
     *               the total number of bytes to write
     * @param offset Offset in this storage's data to start writing to (0-based)
     *
     * @since 1.0
     */
    void writeBlock(ByteBuffer buffer, long offset);

    /**
     * Write a block of data to this storage, starting with a given offset.
     * Number of bytes to be written is determined by block's length.
     * Storage must throw an exception
     * if offset > ({@link #capacity()} - block.length)
     *
     * @param block Block of data to write to this storage.
     * @param offset Offset in this storage's data to start writing to (0-based)
     *
     * @since 1.0
     */
    void writeBlock(byte[] block, long offset);

    /**
     * @return Total maximum capacity of this storage.
     * @since 1.0
     */
    long capacity();
}
