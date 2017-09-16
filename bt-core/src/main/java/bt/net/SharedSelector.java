/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.net;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Optional;
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

    public Optional<SelectionKey> keyFor(SelectableChannel channel) {
        return Optional.ofNullable(channel.keyFor(delegate));
    }
}
