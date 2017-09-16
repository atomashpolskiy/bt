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

import bt.magnet.UtMetadata;
import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.protocol.Message;
import bt.runtime.Config;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MetadataProducer {

    private final Supplier<Torrent> torrentSupplier;

    // initialized on the first metadata request if the torrent is present
    private volatile ExchangedMetadata metadata;

    private final ConcurrentMap<Peer, Queue<Message>> outboundMessages;

    private final int metadataExchangeBlockSize;

    public MetadataProducer(Supplier<Torrent> torrentSupplier,
                            Config config) {
        this.torrentSupplier = torrentSupplier;
        this.outboundMessages = new ConcurrentHashMap<>();
        this.metadataExchangeBlockSize = config.getMetadataExchangeBlockSize();
    }

    @Consumes
    public void consume(UtMetadata message, MessageContext context) {
        Peer peer = context.getPeer();
        // being lenient herer and not checking if the peer advertised ut_metadata support
        switch (message.getType()) {
            case REQUEST: {
                // TODO: spam protection
                processMetadataRequest(peer, message.getPieceIndex());
            }
            default: {
                // ignore
            }
        }
    }

    private void processMetadataRequest(Peer peer, int pieceIndex) {
        Message response;

        Torrent torrent = torrentSupplier.get();
        if (torrent == null || torrent.isPrivate()) {
            // reject all requests if:
            // - we don't have the torrent yet
            // - torrent is private
            response = UtMetadata.reject(pieceIndex);
        } else {
            if (metadata == null) {
                metadata = new ExchangedMetadata(torrent.getSource().getExchangedMetadata(), metadataExchangeBlockSize);
            }

            response = UtMetadata.data(pieceIndex, metadata.length(), metadata.getBlock(pieceIndex));
        }

        getOrCreateOutboundMessages(peer).add(response);
    }

    private Queue<Message> getOrCreateOutboundMessages(Peer peer) {
        Queue<Message> queue = outboundMessages.get(peer);
        if (queue == null) {
            queue = new LinkedBlockingQueue<>();
            Queue<Message> existing = outboundMessages.putIfAbsent(peer, queue);
            if (existing != null) {
                queue = existing;
            }
        }
        return queue;
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();

        Queue<Message> queue = outboundMessages.get(peer);
        if (queue != null && queue.size() > 0) {
            messageConsumer.accept(queue.poll());
        }
    }
}
