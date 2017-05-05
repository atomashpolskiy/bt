package bt.data;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Data range synchronized with a private or shared lock.
 *
 * @since 1.2
 */
class SynchronizedDataRange implements DataRange {

    private final DataRange delegate;

    /**
     * Shared lock for this range and all its' child subranges
     */
    private final ReadWriteLock lock;

    /**
     * Create a data range synchronized with a private lock.
     *
     * @since 1.2
     */
    SynchronizedDataRange(DataRange delegate) {
        this.delegate = delegate;
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Create a data range synchronized with a shared lock.
     *
     * @since 1.2
     */
    SynchronizedDataRange(DataRange delegate, ReadWriteLock lock) {
        this.delegate = delegate;
        this.lock = lock;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.2
     */
    @Override
    public long length() {
        return delegate.length();
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.2
     */
    @Override
    public DataRange getSubrange(long offset, long length) {
        return new SynchronizedDataRange(delegate.getSubrange(offset, length), lock);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.2
     */
    @Override
    public DataRange getSubrange(long offset) {
        return new SynchronizedDataRange(delegate.getSubrange(offset), lock);
    }

    /**
     * {@inheritDoc}
     *
     * Blocks current thread if there are concurrent write operations in progress.
     * Blocks all concurrent write operations.
     *
     * @since 1.2
     */
    @Override
    public byte[] getBytes() {
        lock.readLock().lock();
        try {
            return delegate.getBytes();
        } finally {
            lock.readLock().unlock();
        }
    }

     /**
     * {@inheritDoc}
     *
     * Blocks current thread if there are concurrent read or write operations in progress.
     * Block all concurrent read or write operations.
     *
     * @since 1.2
     */
    @Override
    public void putBytes(byte[] block) {
        lock.writeLock().lock();
        try {
            delegate.putBytes(block);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void visitUnits(DataRangeVisitor visitor) {
        delegate.visitUnits(visitor);
    }
}
