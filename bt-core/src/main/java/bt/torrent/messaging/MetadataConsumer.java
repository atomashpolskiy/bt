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
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.protocol.Message;
import bt.protocol.extended.ExtendedHandshake;
import bt.runtime.Config;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MetadataConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConsumer.class);

    private static final Duration FIRST_BLOCK_ARRIVAL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WAIT_BEFORE_REREQUESTING_AFTER_REJECT = Duration.ofSeconds(10);

    private final ConcurrentMap<Peer, Long> peersWithoutMetadata;

    private final Set<Peer> supportingPeers;
    private final ConcurrentMap<Peer, Long> requestedFirstPeers;
    private final Set<Peer> requestedAllPeers;

    private volatile ExchangedMetadata metadata;

    private final IMetadataService metadataService;

    private final TorrentId torrentId;

    // set immediately after metadata has been fetched and verified
    private final AtomicReference<Torrent> torrent;

    private final int metadataExchangeBlockSize;
    private final int metadataExchangeMaxSize;

    public MetadataConsumer(IMetadataService metadataService,
                            TorrentId torrentId,
                            Config config) {

        this.peersWithoutMetadata = new ConcurrentHashMap<>();

        this.supportingPeers = ConcurrentHashMap.newKeySet();
        this.requestedFirstPeers = new ConcurrentHashMap<>();
        this.requestedAllPeers = ConcurrentHashMap.newKeySet();

        this.metadataService = metadataService;

        this.torrentId = Objects.requireNonNull(torrentId);
        this.torrent = new AtomicReference<>();

        this.metadataExchangeBlockSize = config.getMetadataExchangeBlockSize();
        this.metadataExchangeMaxSize = config.getMetadataExchangeMaxSize();
    }

    @Consumes
    public void consume(ExtendedHandshake handshake, MessageContext messageContext) {
        if (handshake.getSupportedMessageTypes().contains("ut_metadata")) {
            // TODO: peer may eventually turn off the ut_metadata extension
            // moreover the extended handshake message type map is additive,
            // so we can't learn about the peer turning off extensions solely from the message
            supportingPeers.add(messageContext.getPeer());
        }
    }

    @Consumes
    public void consume(UtMetadata message, MessageContext context) {
        Peer peer = context.getPeer();
        // being lenient herer and not checking if the peer advertised ut_metadata support
        switch (message.getType()) {
            case DATA: {
                int totalSize = message.getTotalSize().get();
                if (totalSize >= metadataExchangeMaxSize) {
                    throw new IllegalStateException("Declared metadata size is too large: " + totalSize +
                            "; max allowed is " + metadataExchangeMaxSize);
                }
                processMetadataBlock(message.getPieceIndex(), totalSize, message.getData().get());
            }
            case REJECT: {
                peersWithoutMetadata.put(peer, System.currentTimeMillis());
            }
            default: {
                // ignore
            }
        }
    }

    private void processMetadataBlock(int pieceIndex, int totalSize, byte[] data) {
        if (metadata == null) {
            metadata = new ExchangedMetadata(totalSize, metadataExchangeBlockSize);
        }

        if (!metadata.isBlockPresent(pieceIndex)) {
            metadata.setBlock(pieceIndex, data);

            if (metadata.isComplete()) {
                byte[] digest = metadata.getSha1Digest();
                if (Arrays.equals(digest, torrentId.getBytes())) {
                    Torrent fetchedTorrent = null;
                    try {
                        fetchedTorrent = metadataService.fromByteArray(metadata.getBytes());
                    } catch (Exception e) {
                        LOGGER.error("Processing of metadata failed: " + torrentId, e);
                        metadata = null;
                    }

                    if (fetchedTorrent != null) {
                        synchronized (torrent) {
                            torrent.set(fetchedTorrent);
                            requestedFirstPeers.clear();
                            requestedAllPeers.clear();
                            torrent.notifyAll();
                        }
                    }
                } else {
                    LOGGER.warn("Metadata fetched, but hash does not match the torrent ID: {}. Will re-fetch", torrentId);
                    // restart the process
                    // TODO: terminate peer connections that the metadata was fetched from?
                    // or just try again with the others?
                    metadata = null;
                }
            }
        }
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        // stop here if metadata has already been fetched
        if (torrent.get() != null) {
            return;
        }

        Peer peer = context.getPeer();
        if (supportingPeers.contains(peer)) {
            if (peersWithoutMetadata.containsKey(peer)) {
                if ((System.currentTimeMillis() - peersWithoutMetadata.get(peer)) >= WAIT_BEFORE_REREQUESTING_AFTER_REJECT.toMillis()) {
                    peersWithoutMetadata.remove(peer);
                }
            }

            if (!peersWithoutMetadata.containsKey(peer)) {
                if (metadata == null) {
                    if (!requestedFirstPeers.containsKey(peer) ||
                            (System.currentTimeMillis() - requestedFirstPeers.get(peer) > FIRST_BLOCK_ARRIVAL_TIMEOUT.toMillis())) {
                        requestedFirstPeers.put(peer, System.currentTimeMillis());
                        // start with the first piece of metadata
                        messageConsumer.accept(UtMetadata.request(0));
                    }
                } else if (!requestedAllPeers.contains(peer)) {
                    requestedAllPeers.add(peer);
                    // TODO: larger metadata should be handled in more intelligent way
                    // starting with block #1 because by now we should have already received block #0
                    for (int i = 1; i < metadata.getBlockCount(); i++) {
                        messageConsumer.accept(UtMetadata.request(i));
                    }
                }
            }
        }
    }

    /**
     * @return Torrent, blocking the calling thread if it hasn't been fetched yet
     */
    public Torrent waitForTorrent() {
        while (torrent.get() == null) {
            synchronized (torrent) {
                if (torrent.get() == null) {
                    try {
                        torrent.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return torrent.get();
    }
}
