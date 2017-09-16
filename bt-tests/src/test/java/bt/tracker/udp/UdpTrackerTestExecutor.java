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

package bt.tracker.udp;

import bt.tracker.TrackerResponse;
import org.junit.rules.ExternalResource;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UdpTrackerTestExecutor extends ExternalResource {

    private final ExecutorService executor;
    private final SingleClientUdpTracker tracker;
    private volatile boolean ignoreErrors;

    public UdpTrackerTestExecutor(SingleClientUdpTracker tracker) {
        this.executor = Executors.newFixedThreadPool(2);
        this.tracker = tracker;
    }

    @Override
    protected void after() {
        executor.shutdownNow();
    }

    private void ignoreInternalErrors() {
        this.ignoreErrors = true;
    }

    void execute(Supplier<TrackerResponse> responseSupplier, Consumer<TrackerResponse> responseConsumer) {

        Set<Throwable> errors = ConcurrentHashMap.newKeySet();

        Object mutex = new Object();
        Consumer<Throwable> errorHandler = error -> {
            synchronized (mutex) {
                if (error != null && !ignoreErrors) {
                    errors.add(error);
                }
            }
        };

        CompletableFuture.anyOf(
                CompletableFuture.runAsync(tracker, executor)
                        .whenComplete((ignore, error) -> errorHandler.accept(error)),
                CompletableFuture.supplyAsync(responseSupplier, executor)
                        .whenComplete((response, error) -> {
                            synchronized (mutex) {
                                if (response != null) {
                                    ignoreInternalErrors();
                                    responseConsumer.accept(response);
                                } else if (error != null) {
                                    errorHandler.accept(error);
                                } else {
                                    throw new NullPointerException("Null response");
                                }
                            }
                        })
        ).join();

        if (!errors.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(out);
            errors.forEach(error -> {
                error.printStackTrace(writer);
                writer.println();
            });
            writer.flush();

            throw new RuntimeException("Unexpected errors:\n\n" + out.toString());
        }
    }
}
