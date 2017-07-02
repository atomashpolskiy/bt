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

    private final ConcurrentMap<Peer, Queue<Message>> outboundMessages;
    private final ConcurrentMap<Peer, Long> peersWithoutMetadata;

    private final Set<Peer> supportingPeers;
    private final Set<Peer> requestedPeers;
    private final ConcurrentMap<Peer, Long> requestedFirstPeers;

    private volatile Range<?> metadata;
    private volatile BlockSet metadataBlocks;

    private IMetadataService metadataService;

    private final TorrentId torrentId;
    private final AtomicReference<Torrent> torrent;

    public MetadataFetcher(IMetadataService metadataService, TorrentId torrentId) {
        this.outboundMessages = new ConcurrentHashMap<>();
        this.peersWithoutMetadata = new ConcurrentHashMap<>();

        this.supportingPeers = ConcurrentHashMap.newKeySet();
        this.requestedPeers = ConcurrentHashMap.newKeySet();
        this.requestedFirstPeers = new ConcurrentHashMap<>();

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
        switch (message.getType()) {
            case REQUEST: {
                // reject all requests as we don't have the full metadata yet
                getOutboundMessages(peer).add(UtMetadata.reject(message.getPieceIndex()));
            }
            case DATA: {
                saveData(message.getPieceIndex(), message.getTotalSize().get(), message.getData().get());
            }
            case REJECT: {
                peersWithoutMetadata.put(peer, System.currentTimeMillis());
            }
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
                        torrent.notifyAll();
                    }
                } catch (Exception e){
                    metadata = null;
                    metadataBlocks = null;
                    throw e;
                }
                return;
            } else {
                // TODO: restart
                metadata = null;
                metadataBlocks = null;
                throw new IllegalStateException("Metadata fetched, but hash does not match the torrent ID");
            }
        }

        Peer peer = context.getPeer();
        if (supportingPeers.contains(peer)) {
            if (metadataBlocks != null) {
                if (!requestedPeers.contains(peer)) {
                    requestedPeers.add(peer);
                    for (int i = 0; i < metadataBlocks.blockCount(); i++) {
                        messageConsumer.accept(UtMetadata.request(i));
                    }
                }
            } else {
                if (!requestedFirstPeers.containsKey(peer) || (System.currentTimeMillis() - requestedFirstPeers.get(peer) > 10000)) {
                    requestedFirstPeers.put(peer, System.currentTimeMillis());
                    messageConsumer.accept(UtMetadata.request(0));
                }
            }
        }

//        if (mightRequestMetadata(peer)) {
//            nextRequest().ifPresent(messageConsumer::accept);
//        }
    }

//    private Optional<Message> nextRequest() {
//        Message message = null;
//        if (metadata == null) {
//            // we don't have anything yet, so let's request the very first piece
//            message = UtMetadata.request(0);
//        } else if (!metadataBlocks.isComplete()) {
//            Optional<Integer> nextPiece = selectNextPiece();
//            if (nextPiece.isPresent()) {
//                message = UtMetadata.request(nextPiece.get());
//            }
//        }
//        return Optional.ofNullable(message);
//    }
//
//    private Optional<Integer> selectNextPiece() {
//        if (pieces == null) {
//            PieceStatistics fakeStatistics = new PieceStatistics() {
//                @Override
//                public int getCount(int pieceIndex) {
//                    return 1;
//                }
//
//                @Override
//                public int getPiecesTotal() {
//                    return metadataBlocks.blockCount();
//                }
//            };
//            pieces = SequentialSelector.sequential().getNextPieces(fakeStatistics);
//        }
//        return pieces.findFirst();
//    }

    private void saveData(int pieceIndex, int totalSize, byte[] data) {
        if (metadata == null) {
            BlockRange<ByteRange> range = Ranges.blockRange(new ByteRange(new byte[totalSize]), BLOCK_SIZE);
            metadata = Ranges.synchronizedRange(range);
            metadataBlocks = Ranges.synchronizedBlockSet(range.getBlockSet());
        }

        if (!metadataBlocks.isPresent(pieceIndex)) {
            metadata.getSubrange(pieceIndex * BLOCK_SIZE).putBytes(data);
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

    public void waitForCompletion() {
        synchronized (torrent) {
            try {
                torrent.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

//    private boolean mightRequestMetadata(Peer peer) {
//        Long lastRejected = peersWithoutMetadata.get(peer);
//        boolean mightRequest = lastRejected == null || (System.currentTimeMillis() - lastRejected >= 30000);
//        if (mightRequest) {
//            peersWithoutMetadata.remove(peer);
//        }
//        return mightRequest;
//    }
}
