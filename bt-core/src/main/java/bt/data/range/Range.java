package bt.data.range;

/**
 * Represents a range of binary data.
 *
 * @since 1.3
 */
public interface Range<T extends Range<T>> {

    /**
     * @return Length of this data range
     *
     * @since 1.3
     */
    long length();

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @param length Length of the new data range
     * @return Subrange of the original data range
     *
     * @since 1.3
     */
    Range<T> getSubrange(long offset, long length);

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @return Subrange of the original data range
     *
     * @since 1.3
     */
    Range<T> getSubrange(long offset);

    /**
     * Get all data in this range
     *
     * @return Data in this range
     *
     * @since 1.3
     */
    byte[] getBytes();

    /**
     * Put data at the beginning of this range.
     *
     * @param block Block of data with length less than or equal to {@link #length()} of this range
     * @throws IllegalArgumentException if data does not fit in this range
     *
     * @since 1.3
     */
    void putBytes(byte[] block);
}
