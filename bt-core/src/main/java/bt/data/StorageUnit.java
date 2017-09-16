/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     * Get current amount of data in this storage.
     *
     * @return Current amount of data in this storage
     * @since 1.1
     */
    long size();
}
