package bt.data;

import bt.metainfo.Torrent;

/**
 * Each torrent is split into chunks (also called "pieces").
 *
 * <p>A chunk is a part of the torrent's collection of files,
 * possibly overlapping several files (in case of multi-file torrents)
 * or being just a part of a single-file torrent.
 *
 * <p>There is a SHA-1 checksum for each chunk in the torrent's metainfo file,
 * so it's effectively an elementary unit of data in BitTorrent.
 * All chunks in a given torrent have the same size
 * (determined by {@link Torrent#getChunkSize()}),
 * except for the last one, which can be smaller.
 *
 * <p>A typical chunk is usually too large to work with at I/O level.
 * So, for the needs of network transfer and storage each chunk is additionally split into "blocks".
 * Size of a block is quite an important parameter of torrent messaging,
 * and it's usually client-specific (meaning that each client is free to choose the concrete value).
 *
 * @since 1.0
 */
public interface ChunkDescriptor {

    /**
     * Expected hash of this chunk's contents as indicated in torrent file.
     *
     * @return Expected hash of this chunk's contents; used to verify integrity of chunk's data
     *
     * @since 1.2
     */
    byte[] getChecksum();

    /**
     * Get the total number of blocks in this chunk.
     *
     * @return Total number of blocks in this chunk.
     * @since 1.0
     */
    int getBlockCount();

    /**
     * Get the size of a block in this chunk.
     *
     * <p>Note that the last block might be smaller due to truncation
     * (i.e. when the chunk's size is not a factor of the size of a block).
     *
     * @return Block size
     * @since 1.0
     */
    long getBlockSize();

    /**
     * Check if a block's data is present.
     *
     * @param blockIndex Index of a block in this chunk
     *                   (0-based, maximum value is <code>{@link #getBlockCount()} - 1</code>)
     * @return true if block's data is present
     * @since 1.0
     */
    boolean isBlockPresent(int blockIndex);

    /**
     * Shortcut method to determine if data for all blocks is present
     *
     * @return true if data for all blocks is present
     * @since 1.2
     */
    boolean isComplete();

    /**
     * Shortcut method to determine if no data is present
     *
     * @return true if no data is present
     * @since 1.2
     */
    boolean isEmpty();

    /**
     * Get chunk's data accessor.
     *
     * @return Chunk's data accessor
     * @since 1.2
     */
    DataRange getData();
}
