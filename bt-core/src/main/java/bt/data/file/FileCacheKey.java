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

import java.nio.file.Path;
import java.util.Objects;

/**
 * The key for a file that can have a handle open and cached for it. This object implements hashCode() and equals()
 * Quickly and efficiently.
 */
public class FileCacheKey {
    private final String pathKey;
    private final Path file;
    private final long capacity;

    /**
     * Construct a new File Cache Key
     *
     * @param file     an absolute path to the file
     * @param capacity the capacity of the file
     */
    public FileCacheKey(Path file, long capacity) {
        this.pathKey = file.toAbsolutePath().toString();
        this.file = file;
        this.capacity = capacity;
    }

    /**
     * Get the file that this key represents
     *
     * @return the file that this key represents
     */
    public Path getFile() {
        return file;
    }

    /**
     * Get the capacity of this file
     *
     * @return the capacity of this file
     */
    public long getCapacity() {
        return capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileCacheKey that = (FileCacheKey) o;
        return pathKey.equals(that.pathKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathKey);
    }
}
