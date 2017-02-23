package bt.torrent.messaging;

import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.protocol.Cancel;
import bt.protocol.Choke;
import bt.protocol.Interested;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Piece;
import bt.protocol.Unchoke;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Messaging interface, that encapsulates all messaging agents (consumers and producers)
 * for a particular torrent processing session.
 * Routes incoming messages directly to interested consumers, based on message types.
 *
 * @since 1.0
 */
class RoutingPeerWorker implements PeerWorker {

    private ConnectionState connectionState;

    private MessageContext context;

    private List<MessageConsumer<Message>> genericConsumers;
    private Map<Class<?>, Collection<MessageConsumer<?>>> typedMessageConsumers;
    private List<MessageProducer> messageProducers;
    private Deque<Message> outgoingMessages;

    private Choker choker;

    public RoutingPeerWorker(Peer peer,
                             Optional<TorrentId> torrentId,
                             List<MessageConsumer<?>> messageConsumers,
                             List<MessageProducer> messageProducers) {
        this.connectionState = new ConnectionState();
        this.context = new MessageContext(torrentId, peer, connectionState);
        this.messageProducers = messageProducers;
        this.outgoingMessages = new LinkedBlockingDeque<>();
        this.choker = Choker.choker();

        initConsumers(messageConsumers);
    }

    @SuppressWarnings("unchecked")
    private void initConsumers(List<MessageConsumer<?>> messageConsumers) {

        List<MessageConsumer<Message>> genericConsumers = new ArrayList<>();
        Map<Class<?>, Collection<MessageConsumer<?>>> typedMessageConsumers = new HashMap<>();

        messageConsumers.forEach(consumer -> {
            Class<?> consumedType = consumer.getConsumedType();
            if (Message.class.equals(consumedType)) {
                genericConsumers.add((MessageConsumer<Message>) consumer);
            } else {
                Collection<MessageConsumer<?>> consumers = typedMessageConsumers.get(consumedType);
                if (consumers == null) {
                    consumers = new ArrayList<>();
                    typedMessageConsumers.put(consumedType, consumers);
                }
                consumers.add(consumer);
            }
        });

        this.genericConsumers = genericConsumers;
        this.typedMessageConsumers = typedMessageConsumers;
    }

    @Override
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    @Override
    public void accept(Message message) {
        doAccept(message);
    }

    private <T extends Message> void doAccept(T message) {
        genericConsumers.forEach(consumer -> {
            consumer.consume(message, context);
            updateConnection();
        });

        Collection<MessageConsumer<?>> consumers = typedMessageConsumers.get(message.getClass());
        if (consumers != null) {
            consumers.forEach(consumer -> {
                @SuppressWarnings("unchecked")
                MessageConsumer<T> typedConsumer = (MessageConsumer<T>) consumer;
                typedConsumer.consume(message, context);
                updateConnection();
            });
        }
    }

    private void postMessage(Message message) {
        if (isUrgent(message)) {
            addUrgent(message);
        } else {
            add(message);
        }
    }

    private boolean isUrgent(Message message) {
        // TODO: this should be done based on priorities
        Class<? extends Message> messageType = message.getClass();
        return Choke.class.equals(messageType) || Unchoke.class.equals(messageType) || Cancel.class.equals(messageType);
    }

    private void add(Message message) {
        outgoingMessages.add(message);
    }

    private void addUrgent(Message message) {
        outgoingMessages.addFirst(message);
    }

    @Override
    public Message get() {
        if (outgoingMessages.isEmpty()) {
            messageProducers.forEach(producer -> {
                producer.produce(this::postMessage, context);
                updateConnection();
            });
        }
        return postProcessOutgoingMessage(outgoingMessages.poll());
    }

    private Message postProcessOutgoingMessage(Message message) {

        if (message == null) {
            return null;
        }

        Class<? extends Message> messageType = message.getClass();

        if (Piece.class.equals(messageType)) {
            Piece piece = (Piece) message;
            // check that peer hadn't sent cancel while we were preparing the requested block
            if (isCancelled(piece)) {
                // dispose of message
                return null;
            } else {
                connectionState.incrementUploaded(piece.getBlock().length);
            }
        }
        if (Interested.class.equals(messageType)) {
            connectionState.setInterested(true);
        }
        if (NotInterested.class.equals(messageType)) {
            connectionState.setInterested(false);
        }
        if (Choke.class.equals(messageType)) {
            connectionState.setShouldChoke(true);
        }
        if (Unchoke.class.equals(messageType)) {
            connectionState.setShouldChoke(false);
        }

        return message;
    }

    private boolean isCancelled(Piece piece) {

        int pieceIndex = piece.getPieceIndex(),
                offset = piece.getOffset(),
                length = piece.getBlock().length;

        return connectionState.getCancelledPeerRequests().remove(Mapper.mapper().buildKey(pieceIndex, offset, length));
    }

    private void updateConnection() {
        choker.handleConnection(connectionState, this::postMessage);
    }
}
