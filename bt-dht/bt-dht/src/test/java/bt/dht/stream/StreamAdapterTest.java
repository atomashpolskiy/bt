/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class StreamAdapterTest {

    @Test
    public void testStreamAdapter_NormalExecution() {
        StreamAdapter<Object> adapter = new StreamAdapter<>();

        ConsumerTask<Object> consumerTask = new ConsumerTask<>(adapter.stream());
        new Thread(consumerTask).start();

        addItemsAndFinish(adapter, new Object(), new Object());

        consumerTask.awaitCompletion();
        assertEquals(2, consumerTask.getItemsConsumed());
        assertSame(ConsumerTask.CompletionKind.NORMAL, consumerTask.getCompletionKind());
    }

    @Test
    public void testStreamAdapter_ExceptionalExecution() {
        StreamAdapter<Object> adapter = new StreamAdapter<>();

        ConsumerTask<Object> consumerTask = new ConsumerTask<>(adapter.stream());
        new Thread(consumerTask).start();

        adapter.finishStream();

        consumerTask.awaitCompletion();
        assertEquals(0, consumerTask.getItemsConsumed());
        assertSame(ConsumerTask.CompletionKind.NORMAL, consumerTask.getCompletionKind());
    }

    private static class ConsumerTask<T> implements Runnable {
        enum CompletionKind {
            NORMAL, EXCEPTIONAL
        }

        private final Stream<T> stream;

        private final AtomicInteger itemsConsumed;

        private final AtomicBoolean completed;
        private volatile CompletionKind completionKind;

        ConsumerTask(Stream<T> stream) {
            this.stream = stream;
            this.itemsConsumed = new AtomicInteger(0);
            this.completed = new AtomicBoolean(false);
        }

        @Override
        public void run() {
            try {
                stream.forEach(t -> itemsConsumed.incrementAndGet());
                completionKind = CompletionKind.NORMAL;
            } catch (Exception e) {
                completionKind = CompletionKind.EXCEPTIONAL;
            }
            synchronized (completed) {
                completed.set(true);
                completed.notifyAll();
            }
        }

        void awaitCompletion() {
            while (!completed.get()) {
                synchronized (completed) {
                    if (!completed.get()) {
                        try {
                            completed.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        int getItemsConsumed() {
            return itemsConsumed.get();
        }

        CompletionKind getCompletionKind() {
            CompletionKind completionKind = this.completionKind;
            if (completionKind == null) {
                throw new IllegalStateException("Not completed yet");
            }
            return completionKind;
        }
    }

    @SafeVarargs
    private static <T> void addItemsAndFinish(StreamAdapter<T> adapter, T... items) {
        for (T item : items) {
            adapter.addItem(item);
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        adapter.finishStream();
    }
}
