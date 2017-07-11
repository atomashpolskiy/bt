package bt.torrent.messaging;

import bt.data.BlockSet;
import bt.data.digest.SHA1Digester;
import bt.data.range.BlockRange;
import bt.data.range.ByteRange;
import bt.data.range.Range;
import bt.data.range.Ranges;
import bt.magnet.UtMetadata;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.protocol.Message;
import bt.protocol.extended.ExtendedHandshake;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MetadataFetcher {
    private static final int BLOCK_SIZE = 16384;
    private static final int MAX_METADATA_SIZE = 2 * 1024 * 1024; // 2MB
    private static final Duration FIRST_BLOCK_ARRIVAL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WAIT_BEFORE_REREQUESTING_AFTER_REJECT = Duration.ofSeconds(10);

    private final ConcurrentMap<Peer, Queue<Message>> outboundMessages;
    private final ConcurrentMap<Peer, Long> peersWithoutMetadata;

    private final Set<Peer> supportingPeers;
    private final ConcurrentMap<Peer, Long> requestedFirstPeers;
    private final Set<Peer> requestedAllPeers;

    private volatile Range<BlockRange<ByteRange>> metadata;
    private volatile BlockSet metadataBlocks;

    private final IMetadataService metadataService;

    private final TorrentId torrentId;

    // set immediately after metadata has been fetched and verified
    private final AtomicReference<Torrent> torrent;

    public MetadataFetcher(IMetadataService metadataService, TorrentId torrentId) {
        this.outboundMessages = new ConcurrentHashMap<>();
        this.peersWithoutMetadata = new ConcurrentHashMap<>();

        this.supportingPeers = ConcurrentHashMap.newKeySet();
        this.requestedFirstPeers = new ConcurrentHashMap<>();
        this.requestedAllPeers = ConcurrentHashMap.newKeySet();

        this.metadataService = metadataService;

        this.torrentId = Objects.requireNonNull(torrentId);
        this.torrent = new AtomicReference<>();
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
            case REQUEST: {
                // TODO: spam protection
                processMetadataRequest(peer, message.getPieceIndex());
            }
            case DATA: {
                int totalSize = message.getTotalSize().get();
                if (totalSize >= MAX_METADATA_SIZE) {
                    throw new IllegalStateException("Declared metadata size is too large: " + totalSize +
                            "; max allowed is " + MAX_METADATA_SIZE);
                }
                processMetadataBlock(message.getPieceIndex(), totalSize, message.getData().get());
            }
            case REJECT: {
                peersWithoutMetadata.put(peer, System.currentTimeMillis());
            }
        }
    }

    private void processMetadataRequest(Peer peer, int pieceIndex) {
        Message response;
        if (torrent.get() == null) {
            // reject all requests as we don't have the full metadata yet
            response = UtMetadata.reject(pieceIndex);
        } else {
            if (pieceIndex >= metadataBlocks.blockCount()) {
                throw new IllegalArgumentException("Invalid piece index: " + pieceIndex +
                        "; expected 0.." + metadataBlocks.blockCount());
            }
            // last piece may be smaller than the rest
            int blockLength = (pieceIndex == metadataBlocks.blockCount() - 1) ? (int) metadataBlocks.lastBlockSize() : BLOCK_SIZE;
            byte[] data = metadata.getSubrange(pieceIndex * BLOCK_SIZE, blockLength).getBytes();
            response = UtMetadata.data(pieceIndex, (int) metadata.length(), data);
        }
        getOutboundMessages(peer).add(response);
    }

    private void processMetadataBlock(int pieceIndex, int totalSize, byte[] data) {
        if (metadata == null) {
            BlockRange<ByteRange> range = Ranges.blockRange(new ByteRange(new byte[totalSize]), BLOCK_SIZE);
            metadata = Ranges.synchronizedRange(range);
            metadataBlocks = Ranges.synchronizedBlockSet(range.getBlockSet());
        }

        if (!metadataBlocks.isPresent(pieceIndex)) {
            metadata.getSubrange(pieceIndex * BLOCK_SIZE).putBytes(data);
        }
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        if (torrent.get() != null) {
            return;
        }

        if (metadataBlocks != null && metadataBlocks.isComplete()) {
            byte[] digest = SHA1Digester.rolling(8000000).digest(metadata);
            if (Arrays.equals(digest, torrentId.getBytes())) {
                try {
                    synchronized (torrent) {
                        torrent.set(metadataService.fromByteArray(metadata.getBytes()));
                        requestedFirstPeers.clear();
                        requestedAllPeers.clear();
                        torrent.notifyAll();
                        return;
                    }
                } catch (Exception e) {
                    metadata = null;
                    metadataBlocks = null;
                    throw e;
                }
            } else {
                // restart the process
                // TODO: terminate peer connections that the metadata was fetched from?
                metadata = null;
                metadataBlocks = null;
                throw new IllegalStateException("Metadata fetched, but hash does not match the torrent ID");
            }
        }

        Peer peer = context.getPeer();
        if (supportingPeers.contains(peer)) {
            if (peersWithoutMetadata.containsKey(peer)) {
                if ((System.currentTimeMillis() - peersWithoutMetadata.get(peer)) >= WAIT_BEFORE_REREQUESTING_AFTER_REJECT.toMillis()) {
                    peersWithoutMetadata.remove(peer);
                }
            }

            if (!peersWithoutMetadata.containsKey(peer)) {
                if (metadataBlocks == null) {
                    if (!requestedFirstPeers.containsKey(peer) ||
                            (System.currentTimeMillis() - requestedFirstPeers.get(peer) > FIRST_BLOCK_ARRIVAL_TIMEOUT.toMillis())) {
                        requestedFirstPeers.put(peer, System.currentTimeMillis());
                        messageConsumer.accept(UtMetadata.request(0));
                    }
                } else if (!requestedAllPeers.contains(peer)) {
                    requestedAllPeers.add(peer);
                    // TODO: larger metadata should be handled in more intelligent way
                    // starting with block #1 because by now we should have already received block #0
                    for (int i = 1; i < metadataBlocks.blockCount(); i++) {
                        messageConsumer.accept(UtMetadata.request(i));
                    }
                }
            }
        }
    }

    private Queue<Message> getOutboundMessages(Peer peer) {
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

    public Torrent fetchTorrent() {
        if (torrent.get() == null) {
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
