package bt.net;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

/**
 * Selector decorator with some convenient extensions, like {@link #wakeupAndRegister(SelectableChannel, int, Object)}.
 *
 * @since 1.5
 */
public class SharedSelector extends Selector {

    private final Selector delegate;
    private volatile boolean registrationInProgress;

    public SharedSelector(Selector delegate) {
        this.delegate = delegate;
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
        return registrationInProgress ? 0 : delegate.selectNow();
    }

    @Override
    public int select(long timeout) throws IOException {
        return registrationInProgress ? 0 : delegate.select(timeout);
    }

    @Override
    public int select() throws IOException {
        return registrationInProgress ? 0 : delegate.select();
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
        registrationInProgress = true;
        try {
            delegate.wakeup();
            channel.register(delegate, ops, attachment);
        } catch (ClosedChannelException e) {
            throw new RuntimeException("Failed to register channel", e);
        } finally {
            registrationInProgress = false;
        }
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
