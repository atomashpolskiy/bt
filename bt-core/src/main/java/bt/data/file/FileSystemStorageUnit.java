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

class FileSystemStorageUnit implements StorageUnit {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStorageUnit.class);

    private final OpenFileCache cache;
    private final FileCacheKey key;
    private Path parent, file;
    private SeekableByteChannel sbc;
    private final long capacity;

    FileSystemStorageUnit(OpenFileCache cache, Path root, String path, long capacity) {
        this.cache = cache;
        this.file = root.resolve(path);
        this.parent = file.getParent();
        this.key = new FileCacheKey(file, capacity);
        this.capacity = capacity;
    }

    @Override
    public int readBlock(ByteBuffer buffer, long offset) {
        if (!cache.existsOnFileSystem(key)) {
            return -1;
        }

        return cache.readBlock(key, buffer, offset);
    }

    @Override
    public void readBlockFully(ByteBuffer buffer, long offset) {
        cache.readBlockFully(key, buffer, offset);
    }

    @Override
    public int writeBlock(ByteBuffer buffer, long offset) {
        return cache.writeBlock(key, buffer, offset);
    }

    @Override
    public void writeBlockFully(ByteBuffer buffer, long offset) {
        cache.writeBlockFully(key, buffer, offset);
    }

    @Override
    public int writeBlock(ByteBufferView buffer, long offset) {
        return this.cache.writeBlock(key, buffer, offset);
    }

    @Override
    public void writeBlockFully(ByteBufferView buffer, long offset) {
        this.cache.writeBlockFully(key, buffer, offset);
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
    public void close() throws IOException{
        cache.close(key);
    }
}