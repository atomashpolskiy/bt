package bt.torrent.messaging;

import bt.net.Peer;
import bt.protocol.Message;

public interface MessageConsumer {

    void consume(Peer peer, ConnectionState connectionState, Message message);
}
