package bt.torrent.messaging;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.module.MessagingAgents;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.runtime.Config;
import bt.torrent.ITorrentSessionFactory;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import com.google.inject.Inject;

import java.util.Set;

/**
 * Default torrent session factory implementation.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class TorrentSessionFactory implements ITorrentSessionFactory {

    private TorrentRegistry torrentRegistry;
    private IPeerRegistry peerRegistry;
    private IPeerConnectionPool connectionPool;
    private IMessageDispatcher messageDispatcher;
    private Set<Object> messagingAgents;
    private Config config;

    @Inject
    public TorrentSessionFactory(TorrentRegistry torrentRegistry,
                                 IPeerRegistry peerRegistry,
                                 IPeerConnectionPool connectionPool,
                                 IMessageDispatcher messageDispatcher,
                                 @MessagingAgents Set<Object> messagingAgents,
                                 Config config) {
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.connectionPool = connectionPool;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
        this.config = config;
    }

    @Override
    public TorrentSession createSession(Torrent torrent) {
        return createSession(torrent.getTorrentId());
    }

    @Override
    public TorrentSession createSession(TorrentId torrentId) {
        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
        return createSession(torrentId, descriptor);
    }

    private TorrentSession createSession(TorrentId torrentId, TorrentDescriptor descriptor) {
        DefaultMessageRouter router = new DefaultMessageRouter(messagingAgents);
        IPeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(router);
        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher, peerWorkerFactory, config);

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, torrentRegistry, router, torrentWorker, torrentId,
                descriptor, config.getMaxPeerConnectionsPerTorrent());

        peerRegistry.addPeerConsumer(torrentId, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        return session;
    }
}
