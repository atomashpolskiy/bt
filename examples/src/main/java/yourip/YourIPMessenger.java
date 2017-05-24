package yourip;

import bt.net.Peer;
import bt.peer.IPeerRegistry;
import bt.protocol.Message;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.MessageContext;
import com.google.inject.Inject;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class YourIPMessenger {

    private final IPeerRegistry peerRegistry;

    private Set<Peer> known;

    @Inject
    public YourIPMessenger(IPeerRegistry peerRegistry) {
        this.peerRegistry = peerRegistry;
        this.known = new HashSet<>();
    }

    @Consumes
    public void consume(YourIP message, MessageContext context) {
        System.out.println("I am " + peerRegistry.getLocalPeer() +
                ", for peer " + context.getPeer() + " my external address is " + message.getAddress());
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();
        if (!known.contains(peer)) {
            String address = context.getPeer().getInetSocketAddress().toString();
            messageConsumer.accept(new YourIP(address));
            known.add(peer);
        }
    }
}
