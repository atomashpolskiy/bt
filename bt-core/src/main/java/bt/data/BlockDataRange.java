package bt.data;

/**
 * @since 1.2
 */
class BlockDataRange implements DataRange {

    private final DataRange delegate;
    private final long offset;

    private final MutableBlockSet blockSet;

    /**
     * Create a block-structured data range.
     *
     * @since 1.2
     */
    BlockDataRange(DataRange delegate, long blockSize) {
        this(delegate, 0, new MutableBlockSet(delegate.length(), blockSize));
    }

    private BlockDataRange(DataRange delegate,
                           long offset,
                           MutableBlockSet blockSet) {
        this.delegate = delegate;
        this.offset = offset;
        this.blockSet = blockSet;
    }

    public BlockSet getBlockSet() {
        return blockSet;
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public DataRange getSubrange(long offset, long length) {
        return new BlockDataRange(delegate.getSubrange(offset, length), offset, blockSet);
    }

    @Override
    public DataRange getSubrange(long offset) {
        return new BlockDataRange(delegate.getSubrange(offset), offset, blockSet);
    }

    @Override
    public byte[] getBytes() {
        return delegate.getBytes();
    }

    @Override
    public void putBytes(byte[] block) {
        delegate.putBytes(block);
        blockSet.markAvailable(offset, block.length);
    }

    @Override
    public void visitUnits(DataRangeVisitor visitor) {
        delegate.visitUnits(visitor);
    }
}
