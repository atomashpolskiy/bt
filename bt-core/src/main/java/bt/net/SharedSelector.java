package bt.net;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Selector decorator with some convenient extensions, like {@link #wakeupAndRegister(SelectableChannel, int, Object)}.
 *
 * @since 1.5
 */
public class SharedSelector extends Selector {

    private final Selector delegate;
    private final ReentrantReadWriteLock registrationLock;
    private volatile boolean selectInProgress;

    public SharedSelector(Selector delegate) {
        this.delegate = delegate;
        this.registrationLock = new ReentrantReadWriteLock();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public SelectorProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<SelectionKey> keys() {
        return delegate.keys();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return delegate.selectedKeys();
    }

    @Override
    public int selectNow() throws IOException {
        registrationLock.writeLock().lock();
        try {
            selectInProgress = true;
            return delegate.selectNow();
        } finally {
            selectInProgress = false;
            registrationLock.writeLock().unlock();
        }
    }

    @Override
    public int select(long timeout) throws IOException {
        registrationLock.writeLock().lock();
        try {
            selectInProgress = true;
            return delegate.select(timeout);
        } finally {
            selectInProgress = false;
            registrationLock.writeLock().unlock();
        }
    }

    @Override
    public int select() throws IOException {
        registrationLock.writeLock().lock();
        try {
            selectInProgress = true;
            return delegate.select();
        } finally {
            selectInProgress = false;
            registrationLock.writeLock().unlock();
        }
    }

    @Override
    public Selector wakeup() {
        return delegate.wakeup();
    }

    /**
     * Atomically wakeup and register the provided channel.
     *
     * @since 1.5
     */
    public void wakeupAndRegister(SelectableChannel channel, int ops, Object attachment) {
        while (!registrationLock.readLock().tryLock()) {
            // try to prevent lots of wakeup calls,
            // when multiple channels are being registered simultaneously;
            // no guarantees though
            if (selectInProgress) {
                delegate.wakeup();
            }
        }
        try {
            channel.register(delegate, ops, attachment);
        } catch (ClosedChannelException e) {
            throw new RuntimeException("Failed to register channel", e);
        } finally {
            registrationLock.readLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
