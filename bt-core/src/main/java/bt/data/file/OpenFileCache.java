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

import bt.net.buffer.ByteBufferView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This is an LRU cache for open files. It prevents having too many opened files by limiting the total possible opened
 * while minimizing file open/close operation by keeping recently read/written files open.
 */
public class OpenFileCache {
    private static final float LOAD_FACTOR = .75f;
    // maps normalized file name to a storage unit for the file
    private final LinkedHashMap<FileCacheKey, CachedOpenFile> cache;

    /**
     * Construct an LRU cache for managing open files
     *
     * @param maxOpenFiles the max number of permitted open files
     */
    public OpenFileCache(int maxOpenFiles) {
        cache = new SizeLimitedFileCache(maxOpenFiles);
    }

    /**
     * Read a block from the provided file cache key
     *
     * @param key    the key that references the files to read the block for
     * @param buffer the buffer to read the block into
     * @param offset the offset of the block
     * @return the number of bytes read.
     */
    public int readBlock(FileCacheKey key, ByteBuffer buffer, long offset) {
        return runOperationOnOpenFile(key, cof -> cof.readBlock(buffer, offset));
    }

    /**
     * Fully read a block from the provided file cache key
     *
     * @param key    the key that references the files to read the block for
     * @param buffer the buffer to read the block into
     * @param offset the offset of the block
     */
    public void readBlockFully(FileCacheKey key, ByteBuffer buffer, long offset) {
        runOperationOnOpenFileConsumer(key, cof -> cof.readBlockFully(buffer, offset));
    }

    /**
     * Write a block to the provided file cache key
     *
     * @param key    the key that references the files to write the block for
     * @param buffer the buffer to write the block into
     * @param offset the offset of the block
     * @return the number of bytes writen.
     */
    public int writeBlock(FileCacheKey key, ByteBuffer buffer, long offset) {
        return runOperationOnOpenFile(key, cof -> cof.writeBlock(buffer, offset));
    }

    /**
     * Fully write a block from the provided file cache key
     *
     * @param key    the key that references the files to write the block for
     * @param buffer the buffer to write the block into
     * @param offset the offset of the block
     */
    public void writeBlockFully(FileCacheKey key, ByteBuffer buffer, long offset) {
        runOperationOnOpenFileConsumer(key, cof -> cof.writeBlockFully(buffer, offset));
    }

    /**
     * Write a block to the provided file cache key
     *
     * @param key    the key that references the files to write the block for
     * @param buffer the buffer to write the block into
     * @param offset the offset of the block
     * @return the number of bytes writen.
     */
    public int writeBlock(FileCacheKey key, ByteBufferView buffer, long offset) {
        return runOperationOnOpenFile(key, cof -> cof.writeBlock(buffer, offset));
    }

    /**
     * Fully write a block from the provided file cache key
     *
     * @param key    the key that references the files to write the block for
     * @param buffer the buffer to write the block into
     * @param offset the offset of the block
     */
    public void writeBlockFully(FileCacheKey key, ByteBufferView buffer, long offset) {
        runOperationOnOpenFileConsumer(key, cof -> cof.writeBlockFully(buffer, offset));
    }

    /**
     * Get the size of the file referred to by key on disk
     * @param key the key of the file
     * @return the size of the file on disk
     */
    public long size(FileCacheKey key) {
        return runOperationOnOpenFile(key, cof -> cof.size());
    }

    /**
     * Flush all buffered data to the file system
     */
    public void flush() {
        List<CachedOpenFile> openedFiles = null;
        // So we don't globally block writes while flushing, we copy the currently open files and flush them.
        synchronized (this) {
            openedFiles = new ArrayList<>(cache.values());
        }

        for (CachedOpenFile openFile : openedFiles) {
            openFile.lockForIoOperation();
            try {
                // this file may have been closed between when we copied the cache values and when we call flush.
                // That's OK - flush handles this.
                openFile.flush();
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not flush file to disk.", ex);
            } finally {
                openFile.unlockForIoOperation();
            }
        }
    }

    /**
     * Check if the the File exists on the file system. First checks if the file system exists in the cache. If yes,
     * the file definitely exists and returns true. If not, it checks the filesystem if the file exists.
     *
     * @param key the key to check whether exists on the file system
     * @return true if it exists on the filesystem, false otherwise
     */
    public boolean existsOnFileSystem(FileCacheKey key) {
        synchronized (this) {
            if (cache.containsKey(key))
                return true;
        }
        return Files.exists(key.getFile());
    }

    /**
     * Run a consumer operation on a file. This function is for convenience
     *
     * @param key       the key of the file to run the operation on
     * @param operation the consumer operation to run
     */
    private void runOperationOnOpenFileConsumer(FileCacheKey key, Consumer<CachedOpenFile> operation) {
        runOperationOnOpenFile(key, cof -> {
            operation.accept(cof);
            return null;
        });
    }

    /**
     * Run an consumer operation on a file. This function handles locking to ensure that the operation is safely run
     *
     * @param key       the key of the file to run the operation on
     * @param operation the operation to run
     * @return the result of the operation
     */
    private <T> T runOperationOnOpenFile(FileCacheKey key, Function<CachedOpenFile, T> operation) {
        CachedOpenFile operationFile = null;
        boolean gotLock = false;
        try {
            synchronized (this) {
                operationFile = cache.computeIfAbsent(key, k -> new CachedOpenFile(k.getFile(), k.getCapacity()));
                // get lock in synchronized block to ensure closed cannot be called by another thread before we get the read lock
                operationFile.lockForIoOperation();
                gotLock = true;
            }
            return operation.apply(operationFile);
        } finally {
            if (gotLock)
                operationFile.unlockForIoOperation();
        }
    }

    /**
     * Close the file associated with this cache key if it is open
     *
     * @param key the key of the file to close
     * @throws IOException on failure to close
     */
    public void close(FileCacheKey key) throws IOException {
        CachedOpenFile closed;
        synchronized (this) {
            closed = cache.remove(key);
            // This is in the synchronized block on purpose to avoid closing while another thread tries to get a readlock
            if (closed != null)
                closed.close();
        }
    }

    /**
     * Close all open files in this storage system.
     *
     * @throws IOException on failure to close file
     */
    public synchronized void close() throws IOException {
        Iterator<Map.Entry<FileCacheKey, CachedOpenFile>> it = this.cache.entrySet().iterator();
        IOException toThrow = null;
        while (it.hasNext()) {
            CachedOpenFile openFileCache = it.next().getValue();
            it.remove();
            try {
                openFileCache.close();
            } catch (IOException ex) {
                if (toThrow != null)
                    ex.printStackTrace();
                toThrow = ex;
            }
        }
        if (null != toThrow)
            throw toThrow;
    }

    /**
     * A size limited cache for open files to avoid excessive open/close calls.
     */
    static class SizeLimitedFileCache extends LinkedHashMap<FileCacheKey, CachedOpenFile> {
        private final int maxOpenFiles;

        public SizeLimitedFileCache(int maxOpenFiles) {
            super((int) Math.ceil(maxOpenFiles / LOAD_FACTOR), LOAD_FACTOR, true);
            this.maxOpenFiles = maxOpenFiles;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<FileCacheKey, CachedOpenFile> eldest) {
            if (size() >= maxOpenFiles) {
                try {
                    eldest.getValue().close();
                } catch (IOException ex) {
                    throw new UncheckedIOException("Failed to close file", ex);
                }
                return true;
            }
            return false;
        }
    }
}
