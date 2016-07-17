package bt.runtime.service.ext.pex;

import bt.metainfo.Torrent;
import bt.net.IPeerConnectionPool;
import bt.net.Peer;
import bt.net.PeerActivityListener;
import bt.protocol.Message;
import bt.runtime.protocol.ext.ExtendedHandshake;
import bt.runtime.protocol.ext.pex.PeerExchange;
import bt.service.PeerSource;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PeerExchangePeerSource implements PeerSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerExchangePeerSource.class);

    private Set<Peer> peersWithPEXSupport;
    private Map<Object, Queue<PeerExchange>> messages;

    @Inject
    public PeerExchangePeerSource(IPeerConnectionPool connectionPool) {

        connectionPool.addConnectionListener(new Listener());

        peersWithPEXSupport = ConcurrentHashMap.newKeySet();
        messages = new ConcurrentHashMap<>();
    }

    @Override
    public Collection<Peer> getPeersForTorrent(Torrent torrent) {

        Queue<PeerExchange> torrentMessages = messages.get(torrent.getInfoHash());
        if (torrentMessages == null) {
            return Collections.emptyList();
        }

        Collection<PeerExchange> torrentMessagesList = new ArrayList<>();
        PeerExchange mesage;
        while ((mesage = torrentMessages.poll()) != null) {
            torrentMessagesList.add(mesage);
        }
        return collectPeers(torrentMessagesList);
    }

    private Collection<Peer> collectPeers(Collection<PeerExchange> messages) {
        Set<Peer> peers = new HashSet<>();
        messages.forEach(message -> {
            message.getAdded().forEach(peers::add);
            message.getDropped().forEach(peers::remove);
        });
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("PEXPeers: " + peers.toString());
        }
        return peers;
    }

    private class Listener implements PeerActivityListener {

        @Override
        public void onPeerConnected(Object torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                                    Consumer<Supplier<Message>> messageSuppliers) {
            messageConsumers.accept(new MessageConsumer(torrentId, peer));
        }

        @Override
        public void onPeerDisconnected(Peer peer) {
            peersWithPEXSupport.remove(peer);
        }
    }

    private class MessageConsumer implements Consumer<Message> {

        private Object torrentId;
        private Peer peer;

        MessageConsumer(Object torrentId, Peer peer) {
            this.torrentId = torrentId;
            this.peer = peer;
        }

        @Override
        public void accept(Message message) {

            if (ExtendedHandshake.class.equals(message.getClass())) {
                ExtendedHandshake handshake = (ExtendedHandshake) message;
                if (handshake.getSupportedMessageTypes().contains("ut_pex")) {
                    peersWithPEXSupport.add(peer);
                }

            } else if (PeerExchange.class.equals(message.getClass())) {
                PeerExchange peerExchange = (PeerExchange) message;

                Queue<PeerExchange> torrentMessages = messages.get(torrentId);
                if (torrentMessages == null) {
                    torrentMessages = new LinkedBlockingQueue<>();
                    messages.put(torrentId, torrentMessages);
                }
                torrentMessages.add(peerExchange);
            }
        }
    }

}
