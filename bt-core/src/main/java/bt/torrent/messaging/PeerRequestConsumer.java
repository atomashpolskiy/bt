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

package bt.torrent.messaging;

import bt.BtException;
import bt.net.Peer;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.Piece;
import bt.protocol.Request;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.data.BlockRead;
import bt.torrent.data.DataWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Consumes block requests, received from the remote peer, and produces blocks.
 *
 * @since 1.0
 */
public class PeerRequestConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerRequestConsumer.class);

    private DataWorker dataWorker;
    private Map<Peer, Queue<BlockRead>> completedRequests;

    public PeerRequestConsumer(DataWorker dataWorker) {
        this.dataWorker = dataWorker;
        this.completedRequests = new ConcurrentHashMap<>();
    }

    @Consumes
    public void consume(Request request, MessageContext context) {
        ConnectionState connectionState = context.getConnectionState();
        if (!connectionState.isChoking()) {
            addBlockRequest(context.getPeer(), request).whenComplete((block, error) -> {
                if (error != null) {
                    throw new RuntimeException("Failed to perform request to read block", error);
                } else if (block.getError().isPresent()) {
                    throw new RuntimeException("Failed to perform request to read block", block.getError().get());
                }
                if (block.isRejected()) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Request to read block could not be completed: " + request);
                    }
                    // called in the same thread, no sync needed
                    connectionState.setShouldChoke(true);
                } else {
                    getCompletedRequestsForPeer(context.getPeer()).add(block);
                }
            });
        }
    }

    private CompletableFuture<BlockRead> addBlockRequest(Peer peer, Request request) {
        return dataWorker.addBlockRequest(peer, request.getPieceIndex(), request.getOffset(), request.getLength());
    }

    private Queue<BlockRead> getCompletedRequestsForPeer(Peer peer) {
        Queue<BlockRead> queue = completedRequests.get(peer);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<>();
            Queue<BlockRead> existing = completedRequests.putIfAbsent(peer, queue);
            if (existing != null) {
                queue = existing;
            }
        }
        return queue;
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();
        Queue<BlockRead> queue = getCompletedRequestsForPeer(peer);
        BlockRead block;
        while ((block = queue.poll()) != null) {
            try {
                messageConsumer.accept(new Piece(block.getPieceIndex(), block.getOffset(), block.getBlock().get()));
            } catch (InvalidMessageException e) {
                throw new BtException("Failed to send PIECE", e);
            }
        }
    }
}
