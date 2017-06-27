package bt.data.range;

import bt.data.DataRange;
import bt.data.DataRangeVisitor;

import java.util.function.Function;

/**
 * Adapter to a range, that indirectly encapsulates DataRange (most probably via delegation chain).
 *
 * @since 1.3
 */
class DelegatingDataRange<T extends Range<T>> implements DataRange, DelegatingRange<T> {

    private Range<T> delegate;
    private Function<Range<T>, DataRange> converter;

    @SuppressWarnings("unchecked")
    static <E extends Range<E>> DelegatingDataRange range(E delegate, Function<E, DataRange> converter) {
        return new DelegatingDataRange<>(delegate, r -> converter.apply((E)r));
    }

    private DelegatingDataRange(Range<T> delegate, Function<Range<T>, DataRange> converter) {
        this.delegate = delegate;
        this.converter = converter;
    }

    @Override
    public void visitUnits(DataRangeVisitor visitor) {
        converter.apply(delegate).visitUnits(visitor);
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public DataRange getSubrange(long offset, long length) {
        return new DelegatingDataRange<>(delegate.getSubrange(offset, length), converter);
    }

    @Override
    public DataRange getSubrange(long offset) {
        return new DelegatingDataRange<>(delegate.getSubrange(offset), converter);
    }

    @Override
    public byte[] getBytes() {
        return delegate.getBytes();
    }

    @Override
    public void putBytes(byte[] block) {
        delegate.putBytes(block);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getDelegate() {
        return (T) delegate;
    }
}
