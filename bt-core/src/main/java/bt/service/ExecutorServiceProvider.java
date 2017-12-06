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

package bt.service;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @since 1.0
 */
public class ExecutorServiceProvider implements Provider<ExecutorService> {

    private volatile ExecutorService executorService;
    private final Object lock;

    @Inject
    public ExecutorServiceProvider() {
        lock = new Object();
    }

    @Override
    public ExecutorService get() {

        if (executorService == null) {
            synchronized (lock) {
                if (executorService == null) {
                    executorService = Executors.newCachedThreadPool(new ThreadFactory() {

                        private AtomicInteger threadId = new AtomicInteger(1);

                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, Objects.requireNonNull(getNamePrefix()) + "-" + threadId.getAndIncrement());
                        }
                    });
                }
            }
        }

        return executorService;
    }

    /**
     * @since 1.6
     */
    protected String getNamePrefix() {
        return "bt.service.executor-thread";
    }
}
