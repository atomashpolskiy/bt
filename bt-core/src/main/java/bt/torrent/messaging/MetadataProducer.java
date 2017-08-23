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
