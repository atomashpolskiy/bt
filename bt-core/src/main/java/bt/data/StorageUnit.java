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
     * <p>Hence, storage must throw an exception if
     * <blockquote>
     * <code>offset &gt; {@link #capacity()} - buffer.remaining()</code>
     * </blockquote>
     *
     * @param buffer Buffer to read bytes into.
     *               Value returned by <b>buffer.remaining()</b> determines the total number of bytes to read.
     * @param offset Index to start reading from (0-based)
     *
     * @since 1.0
     */
    void readBlock(ByteBuffer buffer, long offset);

    /**
     * Read a block of data, starting with a given offset.
     * <p>Storage must throw an exception if
     * <blockquote>
     * <code>offset &gt; {@link #capacity()} - length</code>
     * </blockquote>
     *
     * @param offset Index to starting reading from (0-based)
     * @param length Total number of bytes to read
     *
     * @since 1.0
     */
    byte[] readBlock(long offset, int length);

    /**
     * Write a block of data from the provided buffer to this storage, starting with a given offset.
     * <p>Number of bytes to be written is determined by {@link Buffer#remaining()}.
     * <p>Hence, storage must throw an exception if
     * <blockquote>
     * <code>offset &gt; {@link #capacity()} - buffer.remaining()</code>
     * </blockquote>
     *
     * @param buffer Buffer containing the block of data to write to this storage.
     *               Value returned by <b>buffer.remaining()</b> determines
     *               the total number of bytes to write.
     * @param offset Offset in this storage's data to start writing to (0-based)
     *
     * @since 1.0
     */
    void writeBlock(ByteBuffer buffer, long offset);

    /**
     * Write a block of data to this storage, starting with a given offset.
     * <p>Number of bytes to be written is determined by block's length.
     * <p>Storage must throw an exception if
     * <blockquote>
     * <code>offset &gt; {@link #capacity()} - block.length</code>
     * </blockquote>
     *
     * @param block Block of data to write to this storage
     * @param offset Offset in this storage's data to start writing to (0-based)
     *
     * @since 1.0
     */
    void writeBlock(byte[] block, long offset);

    /**
     * Get total maximum capacity of this storage.
     *
     * @return Total maximum capacity of this storage
     * @since 1.0
     */
    long capacity();

    /**
     * Checks if this storage has any data.
     * E.g. a file storage might check if the underlying file exists and is not empty.
     *
     * @return true if this storage has any data
     * @since 1.1
     */
    boolean hasData();
}
