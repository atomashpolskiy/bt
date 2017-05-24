package bt.torrent.messaging;

import bt.metainfo.Torrent;
import bt.module.MessagingAgents;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.runtime.Config;
import bt.data.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.ITorrentSessionFactory;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionParams;
import bt.torrent.data.DataWorker;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.SelectorAdapter;
import bt.torrent.selector.ValidatingSelector;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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
    private IDataWorkerFactory dataWorkerFactory;
    private Set<Object> messagingAgents;
    private Config config;

    @Inject
    public TorrentSessionFactory(TorrentRegistry torrentRegistry,
                                 IPeerRegistry peerRegistry,
                                 IPeerConnectionPool connectionPool,
                                 IMessageDispatcher messageDispatcher,
                                 IDataWorkerFactory dataWorkerFactory,
                                 @MessagingAgents Set<Object> messagingAgents,
                                 Config config) {
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.connectionPool = connectionPool;
        this.messageDispatcher = messageDispatcher;
        this.dataWorkerFactory = dataWorkerFactory;
        this.messagingAgents = messagingAgents;
        this.config = config;
    }

    @Override
    public TorrentSession createSession(Torrent torrent, TorrentSessionParams params) {
        TorrentDescriptor descriptor = getTorrentDescriptor(torrent);
        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield);
        PieceSelector selector = createSelector(params, bitfield);

        DataWorker dataWorker = createDataWorker(descriptor);
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);
        IPeerWorkerFactory peerWorkerFactory = createPeerWorkerFactory(descriptor, pieceStatistics, dataWorker);

        TorrentWorker torrentWorker = new TorrentWorker(torrent.getTorrentId(), bitfield, assignments,
                pieceStatistics, messageDispatcher, peerWorkerFactory, config);
        TorrentSession session = new DefaultTorrentSession(connectionPool, torrentWorker,
                torrent, bitfield, config.getMaxPeerConnectionsPerTorrent());

        peerRegistry.addPeerConsumer(torrent, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        return session;
    }

    private PieceSelector createSelector(TorrentSessionParams params,
                                         Bitfield bitfield) {
        Predicate<Integer> validator = new IncompletePiecesValidator(bitfield);
        PieceSelector selector = params.getPieceSelector();
        if (selector == null) {
            selector = new SelectorAdapter(params.getSelectionStrategy(), validator);
        } else {
            selector = new ValidatingSelector(validator, selector);
        }
        return selector;
    }

    private TorrentDescriptor getTorrentDescriptor(Torrent torrent) {
        return torrentRegistry.getDescriptor(torrent)
                .orElseThrow(() -> new IllegalStateException("Unknown torrent: " + torrent));
    }

    private DataWorker createDataWorker(TorrentDescriptor descriptor) {
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }

    private IPeerWorkerFactory createPeerWorkerFactory(TorrentDescriptor descriptor,
                                                       BitfieldBasedStatistics pieceStatistics,
                                                       DataWorker dataWorker) {
        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();

        List<Object> messagingAgents = new ArrayList<>();
        messagingAgents.add(GenericConsumer.consumer());
        messagingAgents.add(new BitfieldConsumer(bitfield, pieceStatistics));
        messagingAgents.add(new PieceConsumer(bitfield, dataWorker));
        messagingAgents.add(new PeerRequestConsumer(dataWorker));
        messagingAgents.add(new RequestProducer(descriptor.getDataDescriptor()));

        messagingAgents.addAll(this.messagingAgents);

        return new PeerWorkerFactory(messagingAgents);
    }
}
