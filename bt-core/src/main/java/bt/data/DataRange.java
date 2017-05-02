package bt.data;

/**
 * Represents a range of binary data, abstracting the mapping of data onto the storage layer.
 * Real data may span over several storage units or reside completely inside a single storage unit.
 *
 * @since 1.2
 */
public interface DataRange {

    /**
     * @return Length of this data range in bytes
     *
     * @since 1.2
     */
    long length();

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @param length Length of the new data range
     * @return Subrange of the original data range
     *
     * @since 1.2
     */
    DataRange getSubrange(long offset, long length);

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @return Subrange of the original data range
     *
     * @since 1.2
     */
    DataRange getSubrange(long offset);

    /**
     * Get all data in this range
     *
     * @return Data in this range
     *
     * @since 1.2
     */
    byte[] getBytes();

    /**
     * Put data at the beginning of this range.
     *
     * @param block Block of data with length less than or equal to {@link #length()} of this range
     * @throws IllegalArgumentException if data does not fit in this range
     *
     * @since 1.2
     */
    void putBytes(byte[] block);

    /**
     * Traverse the storage units in this data range.
     *
     * @since 1.2
     */
    void visitUnits(DataRangeVisitor visitor);
}
