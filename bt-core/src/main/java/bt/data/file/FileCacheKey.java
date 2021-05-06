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
