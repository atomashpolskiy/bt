package bt.data;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @since 1.2
 */
class SynchronizedBlockSet implements BlockSet {

    private final BlockSet delegate;
    private final ReentrantLock lock;

    /**
     * @since 1.2
     */
    SynchronizedBlockSet(BlockSet delegate) {
        this.delegate = delegate;
        this.lock = new ReentrantLock();
    }

    @Override
    public int blockCount() {
        return delegate.blockCount();
    }

    @Override
    public long length() {
        return delegate.length();
    }

    @Override
    public long blockSize() {
        return delegate.blockSize();
    }

    @Override
    public long lastBlockSize() {
        return delegate.lastBlockSize();
    }

    @Override
    public boolean isPresent(int blockIndex) {
        lock.lock();
        try {
            return delegate.isPresent(blockIndex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isComplete() {
        lock.lock();
        try {
            return delegate.isComplete();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return delegate.isEmpty();
        } finally {
            lock.unlock();
        }
    }
}
