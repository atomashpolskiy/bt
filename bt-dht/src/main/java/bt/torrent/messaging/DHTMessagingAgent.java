package bt.torrent.messaging;

import bt.dht.DHTConfig;
import bt.dht.DHTService;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.protocol.Message;
import bt.protocol.Port;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import com.google.inject.Inject;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @since 1.1
 */
public class DHTMessagingAgent {

    private DHTService dhtService;
    private DHTConfig config;

    private Set<Peer> peersAwaitingPort;

    @Inject
    public DHTMessagingAgent(DHTService dhtService, DHTConfig config) {
        this.dhtService = dhtService;
        this.config = config;
        this.peersAwaitingPort = ConcurrentHashMap.newKeySet();
    }

    public void shouldCommunicatePortTo(Peer peer) {
        this.peersAwaitingPort.add(peer);
    }

    @Consumes
    public void consume(Port port, MessageContext context) {
        Peer dhtNode = new InetPeer(context.getPeer().getInetAddress(), port.getPort());
        dhtService.addNode(dhtNode);
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        if (peersAwaitingPort.remove(context.getPeer())) {
            messageConsumer.accept(new Port(config.getListeningPort()));
        }
    }
}
