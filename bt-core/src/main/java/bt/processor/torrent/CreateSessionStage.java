package bt.processor.torrent;

import bt.data.Bitfield;
import bt.event.EventSource;
import bt.metainfo.TorrentId;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.DefaultTorrentSessionState;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.DefaultMessageRouter;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.MessageRouter;
import bt.torrent.messaging.PeerWorkerFactory;
import bt.torrent.messaging.TorrentWorker;

import java.util.Set;
import java.util.function.Supplier;

public class CreateSessionStage<C extends TorrentContext> extends BaseProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private EventSource eventSource;
    private IPeerConnectionPool connectionPool;
    private IMessageDispatcher messageDispatcher;
    private Set<Object> messagingAgents;
    private Config config;

    public CreateSessionStage(ProcessingStage<C> next,
                              TorrentRegistry torrentRegistry,
                              EventSource eventSource,
                              IPeerConnectionPool connectionPool,
                              IMessageDispatcher messageDispatcher,
                              Set<Object> messagingAgents,
                              Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.eventSource = eventSource;
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
        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher, connectionPool, peerWorkerFactory,
                bitfieldSupplier, assignmentsSupplier, statisticsSupplier, eventSource, config);

        context.setState(new DefaultTorrentSessionState(descriptor, torrentWorker));
        context.setRouter(router);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
