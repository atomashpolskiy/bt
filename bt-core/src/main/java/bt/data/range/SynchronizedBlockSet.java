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

package bt.data.range;

import bt.data.BlockSet;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @since 1.2
 */
public class SynchronizedBlockSet implements BlockSet {

    private final BlockSet delegate;
    private final Lock lock;

    /**
     * @since 1.2
     */
    public SynchronizedBlockSet(BlockSet delegate) {
        this.delegate = delegate;
        this.lock = new ReentrantLock();
    }

    /**
     * @since 1.9
     */
    public SynchronizedBlockSet(BlockSet delegate, SynchronizedRange<?> parentRange) {
        this.delegate = delegate;
        this.lock = parentRange.getLock().readLock();
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

    @Override
    public void clear() {
        lock.lock();
        try {
            delegate.clear();
        } finally {
            lock.unlock();
        }
    }
}
