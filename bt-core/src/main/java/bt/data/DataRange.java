package bt.data;

import bt.data.range.Range;

/**
 * Represents a range of binary data, abstracting the mapping of data onto the storage layer.
 * Real data may span over several storage units or reside completely inside a single storage unit.
 *
 * @since 1.2
 */
public interface DataRange extends Range<DataRange> {

    /**
     * Traverse the storage units in this data range.
     *
     * @since 1.2
     */
    void visitUnits(DataRangeVisitor visitor);

    /**
     * {@inheritDoc}
     *
     * @since 1.3
     */
    DataRange getSubrange(long offset, long length);

    /**
     * {@inheritDoc}
     *
     * @since 1.3
     */
    DataRange getSubrange(long offset);
}
