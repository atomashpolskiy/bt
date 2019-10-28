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

package bt.torrent.data;

import bt.data.ChunkDescriptor;
import bt.data.ChunkVerifier;
import bt.data.DataDescriptor;
import bt.data.DataRange;
import bt.net.Peer;
import bt.net.buffer.BufferedData;
import bt.service.IRuntimeLifecycleBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class DefaultDataWorker implements DataWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataWorker.class);

    private DataDescriptor data;
    private ChunkVerifier verifier;

    private final ExecutorService executor;
    private final ExecutorService verifierExecutor;
    private final int maxPendingTasks;
    private final AtomicInteger pendingTasksCount;

    public DefaultDataWorker(IRuntimeLifecycleBinder lifecycleBinder,
                             DataDescriptor data,
                             ChunkVerifier verifier,
                             int maxQueueLength) {

        this.data = data;
        this.verifier = verifier;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            private AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "bt.torrent.data.worker-" + i.incrementAndGet());
            }
        });
        this.verifierExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            private AtomicInteger i = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "bt.torrent.data.verifier-" + i.incrementAndGet());
            }
        });
        this.maxPendingTasks = maxQueueLength;
        this.pendingTasksCount = new AtomicInteger();

        lifecycleBinder.onShutdown("Shutdown data worker for descriptor: " + data, this.executor::shutdownNow);
        lifecycleBinder.onShutdown("Shutdown data verifier for descriptor: " + data, this.verifierExecutor::shutdownNow);
    }

    @Override
    public BlockRead getBlock(Peer peer, int pieceIndex, int offset, int length) {
        if (!data.getBitfield().isVerified(pieceIndex)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Rejecting request to read block because the piece is not verified yet:" +
                        " piece index {"+pieceIndex+"}, offset {"+offset+"}, length {"+length+"}, peer {"+peer+"}");
            }
            return BlockRead.rejected(peer, pieceIndex, offset, length);
        } else {
            try {
                ChunkDescriptor chunk = data.getChunkDescriptors().get(pieceIndex);
                DataRange block = chunk.getData().getSubrange(offset, length);
                return BlockRead.ready(peer, pieceIndex, offset, length, block::getBytesFully);
            } catch (Throwable e) {
                LOGGER.error("Failed to perform request to read block:" +
                        " piece index {"+pieceIndex+"}, offset {"+offset+"}, length {"+length+"}, peer {"+peer+"}", e);
                return BlockRead.exceptional(peer, e, pieceIndex, offset, length);
            }
        }
    }

    @Override
    public CompletableFuture<BlockWrite> addBlock(Peer peer, int pieceIndex, int offset, BufferedData buffer) {
        if (pendingTasksCount.get() >= maxPendingTasks) {
            LOGGER.warn("Can't accept write block request -- queue is full");
            return CompletableFuture.completedFuture(BlockWrite.rejected(peer, pieceIndex, offset, buffer.length()));
        } else {
            pendingTasksCount.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (data.getBitfield().isVerified(pieceIndex)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Rejecting request to write block because the chunk is already complete and verified: " +
                                    "piece index {" + pieceIndex + "}, offset {" + offset + "}, length {" + buffer.length() + "}");
                        }
                        return BlockWrite.rejected(peer, pieceIndex, offset, buffer.length());
                    }

                    ChunkDescriptor chunk = data.getChunkDescriptors().get(pieceIndex);
                    chunk.getData().getSubrange(offset).putBytes(buffer.buffer());
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Successfully processed block: " +
                                "piece index {" + pieceIndex + "}, offset {" + offset + "}, length {" + buffer.length() + "}");
                    }

                    CompletableFuture<Boolean> verificationFuture = null;
                    if (chunk.isComplete()) {
                        verificationFuture = CompletableFuture.supplyAsync(() -> {
                            boolean verified = verifier.verify(chunk);
                            if (verified) {
                                data.getBitfield().markVerified(pieceIndex);
                            } else {
                                // reset data
                                chunk.clear();
                            }
                            return verified;
                        }, verifierExecutor);
                    }

                    return BlockWrite.complete(peer, pieceIndex, offset, buffer.length(), verificationFuture);
                } catch (Throwable e) {
                    return BlockWrite.exceptional(peer, e, pieceIndex, offset, buffer.length());
                } finally {
                    pendingTasksCount.decrementAndGet();
                    buffer.dispose();
                }
            }, executor);
        }
    }
}
