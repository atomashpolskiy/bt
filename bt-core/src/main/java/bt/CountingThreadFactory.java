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

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @since 1.6
 */
public class CountingThreadFactory implements ThreadFactory {

    /**
     * @since 1.6
     */
    public static CountingThreadFactory factory(String prefix) {
        return new CountingThreadFactory(prefix, false);
    }

    /**
     * @since 1.6
     */
    public static CountingThreadFactory daemonFactory(String prefix) {
        return new CountingThreadFactory(prefix, true);
    }

    private final String prefix;
    private final boolean daemon;
    private final AtomicLong threadIndex;

    public CountingThreadFactory(String prefix, boolean daemon) {
        this.prefix = Objects.requireNonNull(prefix);
        this.daemon = daemon;
        this.threadIndex = new AtomicLong(0);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, newThreadName());
        if (daemon) {
            t.setDaemon(true);
        }
        return t;
    }

    private String newThreadName() {
        return String.format("%s-%d", prefix, threadIndex.getAndIncrement());
    }
}
