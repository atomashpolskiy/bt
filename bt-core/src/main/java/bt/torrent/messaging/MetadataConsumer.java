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
import bt.protocol.Message;
import bt.runtime.Config;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MetadataConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataConsumer.class);

    private static final String UT_METADATA_EXTENSION = "ut_metadata";

    private static final Duration FIRST_BLOCK_ARRIVAL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WAIT_BEFORE_REREQUESTING_AFTER_REJECT = Duration.ofSeconds(10);

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

        this.metadataService = metadataService;

        this.torrentId = Objects.requireNonNull(torrentId);
        this.torrent = new AtomicReference<>();

        this.metadataExchangeBlockSize = config.getMetadataExchangeBlockSize();
        this.metadataExchangeMaxSize = config.getMetadataExchangeMaxSize();
    }

    @Consumes
    public void consume(UtMetadata message, MessageContext context) {
        // being lenient here and not checking if the peer advertised ut_metadata support
        switch (message.getType()) {
            case DATA: {
                int totalSize = message.getTotalSize().get();
                if (totalSize >= metadataExchangeMaxSize) {
                    throw new IllegalStateException("Declared metadata size is too large: " + totalSize +
                            "; max allowed is " + metadataExchangeMaxSize);
                }
                processMetadataBlock(message.getPieceIndex(), totalSize, message.getData().get());
            }
            break;
            case REJECT: {
                context.getConnectionState().getOrBuildExtensionState(MetadataConsumerState.class)
                        .setWithoutMetadata(System.currentTimeMillis());
            }
            break;
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

        if (context.getPeer().supportsExtension(UT_METADATA_EXTENSION)) {
            MetadataConsumerState state = context.getConnectionState().getOrBuildExtensionState(MetadataConsumerState.class);
            final long now = System.currentTimeMillis();
            if (state.getWithoutMetadata() != MetadataConsumerState.NOT_RECENTLY &&
                    (now - state.getWithoutMetadata()) >= WAIT_BEFORE_REREQUESTING_AFTER_REJECT.toMillis()) {
                state.setWithoutMetadata(MetadataConsumerState.NOT_RECENTLY);
            }

            if (state.getWithoutMetadata() == MetadataConsumerState.NOT_RECENTLY) {
                if (metadata == null) {
                    final long requestedFirstPeers = state.getRequestedFirstPeers();
                    if (requestedFirstPeers != MetadataConsumerState.NOT_RECENTLY ||
                            (now - requestedFirstPeers > FIRST_BLOCK_ARRIVAL_TIMEOUT.toMillis())) {
                        state.setRequestedFirstPeers(now);
                        // start with the first piece of metadata
                        messageConsumer.accept(UtMetadata.request(0));
                    }
                } else if (!state.isRequestedAllPeers()) {
                    state.setRequestedAllPeers(true);
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

    /**
     * A class which stores the state for the metadata sharing extension
     */
    public static class MetadataConsumerState implements ExtensionConnectionState<MetadataConsumerState> {
        private static final long NOT_RECENTLY = -1;

        // the last time that we probed this peer for metadata and got a reject
        private long withoutMetadata = NOT_RECENTLY;

        // the last time we requested the first metadata block from this peer.
        private long requestedFirstPeers = NOT_RECENTLY;
        // whether we requested all metadata blocks from this peer
        private boolean requestedAllPeers = false;

        public long getWithoutMetadata() {
            return withoutMetadata;
        }

        public void setWithoutMetadata(long withoutMetadata) {
            this.withoutMetadata = withoutMetadata;
        }

        public long getRequestedFirstPeers() {
            return requestedFirstPeers;
        }

        public void setRequestedFirstPeers(long requestedFirstPeers) {
            this.requestedFirstPeers = requestedFirstPeers;
        }

        public boolean isRequestedAllPeers() {
            return requestedAllPeers;
        }

        public void setRequestedAllPeers(boolean requestedAllPeers) {
            this.requestedAllPeers = requestedAllPeers;
        }
    }
}
