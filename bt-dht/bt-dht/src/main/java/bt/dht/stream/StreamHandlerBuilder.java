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
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamHandlerBuilder<T> {
    private final BlockingQueue<T> blockingQueue;
    private final Consumer<T> itemHandler;
    private final Supplier<Boolean> hasNextSupplier;

    public StreamHandlerBuilder(Supplier<Boolean> hasNextSupplier) {
        this.blockingQueue = new LinkedBlockingQueue<>();
        this.itemHandler = blockingQueue::add;
        this.hasNextSupplier = hasNextSupplier;
    }

    public Consumer<T> getItemHandler() {
        return itemHandler;
    }

    public Stream<T> stream() {
        int characteristics = Spliterator.NONNULL;
        return StreamSupport.stream(() -> Spliterators.spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return hasNextSupplier.get();
            }

            @Override
            public T next() {
                try {
                    return blockingQueue.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, characteristics), characteristics, false);
    }
}
