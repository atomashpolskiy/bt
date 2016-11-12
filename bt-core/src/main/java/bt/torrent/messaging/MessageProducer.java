package bt.torrent.messaging;

import bt.net.Peer;
import bt.protocol.Message;

import java.util.function.Consumer;

public interface MessageProducer {

    void produce(Peer peer, ConnectionState connectionState, Consumer<Message> messageConsumer);
}
