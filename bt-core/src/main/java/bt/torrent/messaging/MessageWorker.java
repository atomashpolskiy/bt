package bt.torrent.messaging;

import bt.net.Peer;
import bt.protocol.Choke;
import bt.protocol.Have;
import bt.protocol.Interested;
import bt.protocol.Message;
import bt.protocol.NotInterested;
import bt.protocol.Piece;
import bt.protocol.Unchoke;

import java.util.Deque;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MessageWorker implements Consumer<Message>, Supplier<Message> {

    private Peer peer;
    private ConnectionState connectionState;

    private Set<MessageConsumer> messageConsumers;
    private Set<MessageProducer> messageProducers;
    private Deque<Message> outgoingMessages;

    private Choker choker;

    public MessageWorker(Peer peer, Set<MessageConsumer> messageConsumers, Set<MessageProducer> messageProducers) {
        this.peer = peer;
        this.connectionState = new ConnectionState();
        this.messageConsumers = messageConsumers;
        this.messageProducers = messageProducers;
        this.outgoingMessages = new LinkedBlockingDeque<>();
        this.choker = new Choker();
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    @Override
    public void accept(Message message) {
        messageConsumers.forEach(consumer -> consumer.consume(peer, connectionState, message));
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
        return Choke.class.equals(messageType) || Unchoke.class.equals(messageType) || Have.class.equals(messageType);
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
                producer.produce(peer, connectionState, this::postMessage);
                choker.handleConnection(connectionState, this::postMessage);
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
}
