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

package bt.it.fixture;

import bt.runtime.Config;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class TestExecutorModule implements Module {

    @Override
    public void configure(Binder binder) {
        // do nothing
    }

    @Provides
    @Singleton
    public ExecutorService provideExecutorService(Config config) {
        ThreadFactory threadFactory = new ThreadFactory() {

            private AtomicInteger threadId = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,
                        "bt-test-pool-" + threadId.getAndIncrement() +
                                " {peer: " + config.getAcceptorAddress() + ":" + config.getAcceptorPort() + "}");
            }
        };
        return new ThreadPoolExecutor(2, 2, 1, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2), threadFactory);
    }
}
