package bt.data.range;

import bt.data.BlockSet;

/**
 * @since 1.3
 */
public class BlockRange<T extends Range<T>> implements Range<BlockRange<T>>, DelegatingRange<T> {

    private final Range<T> delegate;
    private final long offset;

    private final MutableBlockSet blockSet;

    /**
     * Create a block-structured data range.
     *
     * @since 1.2
     */
    BlockRange(Range<T> delegate, long blockSize) {
        this(delegate, 0, new MutableBlockSet(delegate.length(), blockSize));
    }

    private BlockRange(Range<T> delegate,
                       long offset,
                       MutableBlockSet blockSet) {
        this.delegate = delegate;
        this.offset = offset;
        this.blockSet = blockSet;
    }

    /**
     * @since 1.3
     */
    public BlockSet getBlockSet() {
        return blockSet;
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public BlockRange<T> getSubrange(long offset, long length) {
        return new BlockRange<>(delegate.getSubrange(offset, length), offset, blockSet);
    }

    @Override
    public BlockRange<T> getSubrange(long offset) {
        return new BlockRange<>(delegate.getSubrange(offset), offset, blockSet);
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

    @SuppressWarnings("unchecked")
    @Override
    public T getDelegate() {
        return (T) delegate;
    }
}
