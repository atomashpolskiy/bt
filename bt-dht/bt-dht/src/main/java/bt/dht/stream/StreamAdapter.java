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

package bt.dht.stream;

import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @param <T> This stream's item type
 * @since 1.7
 */
public class StreamAdapter<T> {
    private final BlockingQueue<T> blockingQueue;
    private final AtomicBoolean streamFinished;

    /**
     * Thread, that is currently in the process of retrieving the next item
     * Can be null, if there is no such thread at the moment
     */
    private final AtomicReference<ConsumerThread> consumerThread;

    public StreamAdapter() {
        this.blockingQueue = new LinkedBlockingQueue<>();
        this.streamFinished = new AtomicBoolean(false);
        this.consumerThread = new AtomicReference<>(null);
    }

    /**
     * Caller should invoke this method, if the underlying stream should not expect
     * to receive any more items and can terminate at will.
     *
     * @since 1.7
     */
    public synchronized void finishStream() {
        streamFinished.set(true);
        ConsumerThread t = consumerThread.get();
        if (t != null && t.isNewItemExpected()) {
            // receiving thread is guaranteed to see the new value in streamFinished,
            // because Thread#interrupt() uses monitor synchronization
            t.getThread().interrupt();
        }
    }

    /**
     * @return true if the item has been added, false otherwise
     * @throws NullPointerException if the item is null
     * @since 1.7
     */
    public synchronized boolean addItem(T item) {
        Objects.requireNonNull(item);
        return !streamFinished.get() && blockingQueue.add(item);
    }

    /**
     * @since 1.7
     */
    public Stream<T> stream() {
        int characteristics = 0; // can't guarantee non-nullness
        return StreamSupport.stream(() -> Spliterators.spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return !blockingQueue.isEmpty() || !streamFinished.get();
            }

            // this stream is expected to be used from one thread at a time
            @Override
            public synchronized T next() {
                T result = null;
                consumerThread.set(new ConsumerThread(Thread.currentThread(), blockingQueue.isEmpty()));
                try {
                    result = blockingQueue.take();
                } catch (InterruptedException e) {
                    // do not throw the exception if the stream has been finished
                    // while current thread has been waiting for the new item
                    if (!streamFinished.get()) {
                        throw new RuntimeException(e);
                    }
                } finally {
                    consumerThread.set(null);
                }
                return result;
            }
        }, characteristics), characteristics, false).filter(item -> item != null);
    }

    private static class ConsumerThread {
        private final Thread thread;
        private final boolean newItemExpected;

        ConsumerThread(Thread thread, boolean newItemExpected) {
            this.thread = thread;
            this.newItemExpected = newItemExpected;
        }

        public Thread getThread() {
            return thread;
        }

        public boolean isNewItemExpected() {
            return newItemExpected;
        }
    }
}
