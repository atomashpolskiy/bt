package bt.data;

/**
 * Status of data in a chunk.
 *
 * @since 1.0
 */
public enum DataStatus {

    /**
     * Chunk has no data yet.
     *
     * @since 1.0
     */
    EMPTY,

    /**
     * Chunk has some data, but is not complete yet.
     *
     * @since 1.0
     */
    INCOMPLETE,

    /**
     * Chunks has all the data, but is not verified yet.
     *
     * @since 1.0
     */
    COMPLETE,

    /**
     * Chunk has all the data and was successfully verified.
     *
     * @since 1.0
     */
    VERIFIED
}
