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

package bt.data.file;

import bt.data.StorageUnit;
import bt.net.buffer.ByteBufferView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class FileSystemStorageUnit implements StorageUnit {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStorageUnit.class);

    private Path parent, file;
    private SeekableByteChannel sbc;
    private long capacity;

    private volatile boolean closed;

    FileSystemStorageUnit(Path root, String path, long capacity) {
        this.file = root.resolve(path);
        this.parent = file.getParent();
        this.capacity = capacity;
        this.closed = true;
    }

    // TODO: this is temporary fix for verification upon app start
    // should be re-done (probably need additional API to know if storage unit is "empty")
    private boolean init(boolean create) {
        if (closed) {
            if (!Files.exists(file)) {
                if (create) {
                    if (!Files.exists(parent)) {
                        try {
                            Files.createDirectories(parent);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to create file storage -- can't create (some of the) directories", e);
                        }
                    }

                    try {
                        Files.createFile(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to create file storage -- " +
                                "can't create new file: " + file.toAbsolutePath(), e);
                    }
                } else {
                    return false;
                }
            }

            try {
                sbc = Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } catch (IOException e) {
                throw new UncheckedIOException("Unexpected I/O error", e);
            }

            closed = false;
        }
        return true;
    }

    @Override
    public synchronized int readBlock(ByteBuffer buffer, long offset) {
        if (closed) {
            if (!init(false)) {
                return -1;
            }
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            sbc.position(offset);
            return sbc.read(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void readBlockFully(ByteBuffer buffer, long offset) {
        int read = 0, total = 0;
        do {
            total += read;
            read = readBlock(buffer, offset + total);
        } while (read >= 0 && buffer.hasRemaining());
    }

    @Override
    public synchronized int writeBlock(ByteBuffer buffer, long offset) {
        if (closed) {
            if (!init(true)) {
                return -1;
            }
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            sbc.position(offset);
            return sbc.write(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void writeBlockFully(ByteBuffer buffer, long offset) {
        int written = 0, total = 0;
        do {
            total += written;
            written = writeBlock(buffer, offset + total);
        } while (written >= 0 && buffer.hasRemaining());
    }

    @Override
    public synchronized int writeBlock(ByteBufferView buffer, long offset) {
        if (closed) {
            if (!init(true)) {
                return -1;
            }
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            sbc.position(offset);
            return buffer.transferTo(sbc);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void writeBlockFully(ByteBufferView buffer, long offset) {
        int written = 0, total = 0;
        do {
            total += written;
            written = writeBlock(buffer, offset + total);
        } while (written >= 0 && buffer.hasRemaining());
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public long size() {
        try {
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            throw new UncheckedIOException("Unexpected I/O error", e);
        }
    }

    @Override
    public String toString() {
        return "(" + capacity + " B) " + file;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                sbc.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close file: " + file, e);
            } finally {
                closed = true;
            }
        }
    }
}