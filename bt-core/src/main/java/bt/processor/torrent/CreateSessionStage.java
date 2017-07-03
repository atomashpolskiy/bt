package bt.processor.torrent;

import bt.data.Bitfield;
import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.DefaultMessageRouter;
import bt.torrent.messaging.DefaultTorrentSession;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.MessageRouter;
import bt.torrent.messaging.PeerWorkerFactory;
import bt.torrent.messaging.TorrentWorker;

import java.util.Set;
import java.util.function.Supplier;

public class CreateSessionStage<C extends TorrentContext> extends BaseProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private IPeerRegistry peerRegistry;
    private IPeerConnectionPool connectionPool;
    private IMessageDispatcher messageDispatcher;
    private Set<Object> messagingAgents;
    private Config config;

    public CreateSessionStage(ProcessingStage<C> next,
                              TorrentRegistry torrentRegistry,
                              IPeerRegistry peerRegistry,
                              IPeerConnectionPool connectionPool,
                              IMessageDispatcher messageDispatcher,
                              Set<Object> messagingAgents,
                              Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.connectionPool = connectionPool;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        TorrentId torrentId = context.getTorrentId().get();
        TorrentDescriptor descriptor = torrentRegistry.register(torrentId);

        MessageRouter router = new DefaultMessageRouter(messagingAgents);
        IPeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(router);

        Supplier<Bitfield> bitfieldSupplier = context::getBitfield;
        Supplier<Assignments> assignmentsSupplier = context::getAssignments;
        Supplier<BitfieldBasedStatistics> statisticsSupplier = context::getPieceStatistics;
        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher, peerWorkerFactory,
                bitfieldSupplier, assignmentsSupplier, statisticsSupplier, config);

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, torrentRegistry, torrentWorker, torrentId,
                descriptor, config.getMaxPeerConnectionsPerTorrent());

        peerRegistry.addPeerConsumer(torrentId, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        context.setSession(session);
        context.setRouter(router);
    }
}
