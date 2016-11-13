package bt.torrent.messaging;

import bt.protocol.Message;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IPeerWorker extends Consumer<Message>, Supplier<Message> {

    ConnectionState getConnectionState();
}
