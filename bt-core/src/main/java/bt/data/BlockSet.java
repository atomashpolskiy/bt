package bt.data;

/**
 * @since 1.2
 */
public interface BlockSet {

    /**
     * Get the total number of blocks in this block set.
     *
     * @return Total number of blocks in this block set.
     * @since 1.2
     */
    int blockCount();

    /**
     * Get the total length of this block set
     *
     * @return Total length of this block set
     * @since 1.2
     */
    long length();

    /**
     * Get the size of a block in this set.
     *
     * <p>Note that the last block might be smaller due to truncation
     * (i.e. when the chunk's size is not a factor of the size of a block).
     *
     * @return Block size
     * @see #lastBlockSize()
     * @since 1.2
     */
    long blockSize();

    /**
     * Get the size of the last block in this set
     *
     * @return Size of the last block
     * @see #blockSize()
     * @since 1.2
     */
    long lastBlockSize();

    /**
     * Check if block is present.
     *
     * @param blockIndex Index of a block in this set
     *                   (0-based, maximum value is <code>{@link #blockCount()} - 1</code>)
     * @return true if block is present
     * @since 1.2
     */
    boolean isPresent(int blockIndex);

    /**
     * Shortcut method to determine if all blocks are present
     *
     * @return true if all blocks are present
     * @since 1.2
     */
    boolean isComplete();

    /**
     * Shortcut method to determine if no blocks are present
     *
     * @return true if no blocks are present
     * @since 1.2
     */
    boolean isEmpty();
}
