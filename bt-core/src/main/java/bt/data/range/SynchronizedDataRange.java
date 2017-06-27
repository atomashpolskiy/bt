package bt.data.range;

import bt.data.DataRange;
import bt.data.DataRangeVisitor;

import java.util.function.Function;

/**
 * Adapter to {@link SynchronizedRange} that indirectly encapsulates DataRange (most probably via delegation chain).
 *
 * @since 1.3
 */
class SynchronizedDataRange<T extends Range<T>> implements DataRange, DelegatingRange<T> {

    private SynchronizedRange<T> delegate;
    private Function<T, DataRange> converter;

    SynchronizedDataRange(SynchronizedRange<T> delegate, Function<T, DataRange> converter) {
        this.delegate = delegate;
        this.converter = converter;
    }

    @Override
    public void visitUnits(DataRangeVisitor visitor) {
        delegate.getLock().writeLock().lock();
        try {
            converter.apply(delegate.getDelegate()).visitUnits(visitor);
        } finally {
            delegate.getLock().writeLock().unlock();
        }
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public DataRange getSubrange(long offset, long length) {
        return new SynchronizedDataRange<>(delegate.getSubrange(offset, length), converter);
    }

    @Override
    public DataRange getSubrange(long offset) {
        return new SynchronizedDataRange<>(delegate.getSubrange(offset), converter);
    }

    @Override
    public byte[] getBytes() {
        return delegate.getBytes();
    }

    @Override
    public void putBytes(byte[] block) {
        delegate.putBytes(block);
    }

    @Override
    public T getDelegate() {
        return delegate.getDelegate();
    }
}
