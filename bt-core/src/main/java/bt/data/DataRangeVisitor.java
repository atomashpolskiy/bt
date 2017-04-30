package bt.data;

/**
 * Traverses a data range on a per-unit basis.
 *
 * @since 1.2
 */
public interface DataRangeVisitor {

    /**
     * Visit (a part of) a file in a range of files
     * @param unit A storage unit
     * @param off Offset that designates the beginning of this chunk's part in the file, inclusive;
     *            visitor must not access the file before this index
     * @param lim Limit that designates the end of this chunk's part in the file, exclusive;
     *            visitor must not access the file at or past this index
     *            (i.e. the limit does not belong to this chunk)
     *
     * @since 1.2
     */
    void visitUnit(StorageUnit unit, long off, long lim);
}
