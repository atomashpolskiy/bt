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
public interface IChunkDescriptor {

    /**
     * Check if this chunk is empty, incomplete, complete or complete-and-verified.
     *
     * @return Status of this chunk
     * @since 1.0
     */
    DataStatus getStatus();

    /**
     * @return Chunk size in bytes
     * @since 1.0
     */
    long getSize();

    // TODO: in fact it's currently a BYTEmask, need to rework
    /**
     * @return Bitmask of blocks in this chunk:
     *         <ul><li>a bit is set to 1 if the corresponding block is complete and verified</li>
     *         <li>a bit is set to 0 if the corresponding block is empty or incomplete</li></ul>
     * @since 1.0
     */
    byte[] getBitfield();

    /**
     * Reads a block of data.
     * <p>Implementations must throw an exception, if
     * <blockquote>
     * <code>offset &gt; {@link #getSize()} - length</code>
     * </blockquote>
     *
     * @param offset Offset from the beginning of this chunk (0-based)
     * @param length Requested block length
     * @since 1.0
     */
    byte[] readBlock(long offset, int length);

    /**
     * Writes a block of data.
     * <p>Implementations must throw an exception, if
     * <blockquote>
     * <code>offset &gt; {@link #getSize()} - block.length</code>
     * </blockquote>
     *
     * @param block A block to write
     * @param offset Offset from the beginning of this chunk (0-based)
     * @since 1.0
     */
    void writeBlock(byte[] block, long offset);

    /**
     * Check integrity of this chunk.
     *
     * @return true if this chunk is complete and succesfully verified
     * @since 1.0
     */
    boolean verify();
}
