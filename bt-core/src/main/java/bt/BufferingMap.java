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

package bt;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Convenient utility class to create maps, that act as temporary buffers
 * for storing values between their initial collection and retrieval for further processing.
 *
 * Thread-safe.
 *
 * @param <K> Key type
 * @param <E> Collection element type
 * @since 1.6
 */
public class BufferingMap<K, E> {

    private final ConcurrentMap<K, Collection<E>> m;
    private final Supplier<? extends Collection<E>> valueSupplier;

    /**
     * @param valueSupplier Factory of new containers for elements
     * @since 1.6
     */
    public BufferingMap(Supplier<? extends Collection<E>> valueSupplier) {
        this.m = new ConcurrentHashMap<>();
        this.valueSupplier = valueSupplier;
    }

    /**
     * @since 1.6
     */
    public synchronized boolean containsKey(K key) {
        Collection<E> values = m.get(key);
        return values != null && values.size() > 0;
    }

    /**
     * Add a new element into the corresponding bucket
     * (a new bucket will be created, if mapping for this key does not exist yet).
     *
     * @since 1.6
     */
    public synchronized void add(K key, E element) {
        Collection<E> values = m.computeIfAbsent(key, it -> valueSupplier.get());
        values.add(element);
    }

    /**
     * Remove and return all elements for a given key.
     *
     * @since 1.6
     */
    public synchronized Collection<E> removeCopy(K key) {
        Collection<E> copy;

        Collection<E> values = m.get(key);
        if (values == null) {
            copy = Collections.emptySet();
        } else {
            copy = valueSupplier.get();
            if (values.size() > 0) {
                copy.addAll(values);
                values.clear();
            }
        }
        return copy;
    }
}
