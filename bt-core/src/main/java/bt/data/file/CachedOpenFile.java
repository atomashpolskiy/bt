/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.data.file;

import bt.data.StorageUnit;
import bt.net.buffer.ByteBufferView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A file that is kept open so reads/writes to the file do not incur the costs of opening and closing a file
 */
class CachedOpenFile implements StorageUnit {
    /**
     * the channel of the open file
     */
    private final FileChannel fc;
    /**
     * A read write lock for file operations to ensure we don't close a channel in the middle of an operation
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    /**
     * The capacity of this file when downloaded. Used for more information on exception cases
     */
    private final long capacity;

    /**
     * Open the specified cache file
     *
     * @param file     the file to open
     * @param capacity the capacity of the file
     */
    CachedOpenFile(Path file, long capacity) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent); // ensure parent directory exists
            }
            // open the file for reading and writing, create it if it isn't present
            fc = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not open file " + file.toAbsolutePath(), ex);
        }
        this.capacity = capacity;
    }

    @Override
    public int readBlock(ByteBuffer buffer, long offset) {
        ensureOpen();

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            return fc.read(buffer, offset);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public int writeBlock(ByteBuffer buffer, long offset) {
        ensureOpen();

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            return fc.write(buffer, offset);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public int writeBlock(ByteBufferView buffer, long offset) {
        ensureOpen();

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            return buffer.transferTo(fc, offset);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public long size() {
        ensureOpen();
        try {
            return fc.size();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Flush any changes to the file to disk. Does not flush metadata
     *
     * @throws IOException on failure to flush
     */
    public void flush() throws IOException {
        // if it was already closed, it was already flushed.
        if (fc.isOpen())
            this.fc.force(false);
    }

    /**
     * Lock this file for an IO Operation. This prevents a potential race condition where the file could be closed
     * while a write operation occurs
     */
    public void lockForIoOperation() {
        this.lock.readLock().lock();
    }

    /**
     * Unlock the file as the operation has completed
     */
    public void unlockForIoOperation() {
        this.lock.readLock().unlock();
    }

    /**
     * Close any allocated resources for this file.
     *
     * @throws IOException on failure to close the file
     */
    @Override
    public void close() throws IOException {
        // wait for any reads to finish
        lock.writeLock().lock();
        try {
            this.fc.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureOpen() {
        if (!fc.isOpen()) {
            throw new IllegalStateException("Cannot access a closed file.");
        }
    }
}
