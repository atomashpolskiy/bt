/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.peer;

import bt.net.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @since 1.1
 */
public abstract class ScheduledPeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledPeerSource.class);

    private final ExecutorService executor;
    private final ReentrantLock lock;
    private final AtomicReference<Future<?>> futureOptional;
    private final Queue<Peer> peers;
    private final Duration trackerTimeout;
    private boolean firstUpdate = true;

    public ScheduledPeerSource(ExecutorService executor, Duration trackerTimeout) {
        this.executor = executor;
        this.trackerTimeout = trackerTimeout;
        this.lock = new ReentrantLock();
        this.futureOptional = new AtomicReference<>();
        this.peers = new LinkedBlockingQueue<>();
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
    }

    @Override
    public boolean update() {
        if (peers.isEmpty()) {
            schedulePeerCollection();
        }
        return !peers.isEmpty();
    }

    private void schedulePeerCollection() {
        if (lock.tryLock()) {
            try {
                if (futureOptional.get() != null) {
                    Future<?> future = futureOptional.get();
                    if (future.isDone()) {
                        waitForFutureAndLogExceptions(future);
                    }
                }

                if (futureOptional.get() == null) {
                    futureOptional.set(executor.submit(() -> collectPeers(peers::add)));

                    // on the first announce, wait for the job to complete, otherwise it can be async
                    if (firstUpdate) {
                        waitForFutureAndLogExceptions(futureOptional.get());
                        firstUpdate = false;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void waitForFutureAndLogExceptions(Future<?> future) {
        try {
            // keep in mind that this only really affects the first (initial)
            // peer collection (see the algorithm in schedulePeerCollection() method)
            long secondsToWait = trackerTimeout == null ? 10 : trackerTimeout.getSeconds();
            future.get(secondsToWait, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            // ignoring InterruptedException here is fine
            // because the peer source's executor will be terminated via a shutdown hook
            LOGGER.warn("Peer collection finished with exception in peer source: " + toString(), e);
        }
        futureOptional.set(null);
    }

    /**
     * @since 1.1
     */
    protected abstract void collectPeers(Consumer<Peer> peerConsumer);
}
