package bt.net;

import bt.protocol.Message;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IMessageDispatcher {

    void addMessageConsumer(Peer sender, Consumer<Message> messageConsumer);

    void addMessageSupplier(Peer recipient, Supplier<Message> messageSupplier);
}
