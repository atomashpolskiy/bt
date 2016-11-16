package bt.metainfo;

import java.util.List;

/**
 * Torrent file descriptor.
 * Contains size and a path relative to the root of the containing torrent.
 *
 * @since 1.0
 */
public interface TorrentFile {

    /**
     * @return Size of this file, in bytes.
     * @since 1.0
     */
    long getSize();

    /**
     * @return A list of UTF-8 encoded strings corresponding to subdirectory names,
     *         the last of which is the actual file name (thus it always contains at least one element).
     * @since 1.0
     */
    List<String> getPathElements();
}
