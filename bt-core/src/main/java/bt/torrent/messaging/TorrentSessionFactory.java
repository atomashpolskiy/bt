package bt.torrent.messaging;

import bt.data.Bitfield;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.module.MessagingAgents;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.runtime.Config;
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

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Default torrent session factory implementation.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class TorrentSessionFactory implements ITorrentSessionFactory {

    private IMetadataService metadataService;
    private TorrentRegistry torrentRegistry;
    private IPeerRegistry peerRegistry;
    private IPeerConnectionPool connectionPool;
    private IMessageDispatcher messageDispatcher;
    private IDataWorkerFactory dataWorkerFactory;
    private Set<Object> messagingAgents;
    private Config config;

    @Inject
    public TorrentSessionFactory(IMetadataService metadataService,
                                 TorrentRegistry torrentRegistry,
                                 IPeerRegistry peerRegistry,
                                 IPeerConnectionPool connectionPool,
                                 IMessageDispatcher messageDispatcher,
                                 IDataWorkerFactory dataWorkerFactory,
                                 @MessagingAgents Set<Object> messagingAgents,
                                 Config config) {
        this.metadataService = metadataService;
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.connectionPool = connectionPool;
        this.messageDispatcher = messageDispatcher;
        this.dataWorkerFactory = dataWorkerFactory;
        this.messagingAgents = messagingAgents;
        this.config = config;
    }

    /*
    Torrent processing stages:
    - get torrent
    - register torrent and create the data descriptor
    - create messaging artifacts such as bitfield and assignments
    - register message agents (which is equivalent to launching torrent download/upload)
     */

    @Override
    public TorrentSession createSession(Torrent torrent, TorrentSessionParams params) {
        TorrentId torrentId = torrent.getTorrentId();

        TorrentDescriptor descriptor = torrentRegistry.register(torrent, params.getStorage());

        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher, config);

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, torrentWorker, torrentId,
                config.getMaxPeerConnectionsPerTorrent());

        MessageRouter router = new MessageRouter(messagingAgents);
        router.addMessagingAgent(GenericConsumer.consumer());

        torrentWorker.setPeerWorkerFactory(new PeerWorkerFactory(router));

        beginTorrentProcessing(torrent, descriptor, params, torrentWorker, session, router);

        peerRegistry.addPeerConsumer(torrentId, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        return session;
    }

    // stage 2
    private void beginTorrentProcessing(Torrent torrent,
                                        TorrentDescriptor descriptor,
                                        TorrentSessionParams params,
                                        TorrentWorker torrentWorker,
                                        DefaultTorrentSession session,
                                        MessageRouter router) {

        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield);
        PieceSelector selector = createSelector(params, bitfield);

        DataWorker dataWorker = createDataWorker(descriptor);
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);

        router.addMessagingAgent(GenericConsumer.consumer());
        router.addMessagingAgent(new BitfieldConsumer(bitfield, pieceStatistics));
        router.addMessagingAgent(new PieceConsumer(bitfield, dataWorker));
        router.addMessagingAgent(new PeerRequestConsumer(dataWorker));
        router.addMessagingAgent(new RequestProducer(descriptor.getDataDescriptor()));

        torrentWorker.setBitfield(bitfield);
        torrentWorker.setAssignments(assignments);
        torrentWorker.setPieceStatistics(pieceStatistics);

        session.setTorrent(torrent);
        session.setBitfield(bitfield);
    }

    @Override
    public TorrentSession createSession(TorrentId torrentId, TorrentSessionParams params) {
        Optional<Torrent> torrentOptional = torrentRegistry.getTorrent(torrentId);
        if (torrentOptional.isPresent()) {
            return createSession(torrentOptional.get(), params);
        }

        torrentRegistry.register(torrentId);

        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher, config);

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, torrentWorker, torrentId,
                config.getMaxPeerConnectionsPerTorrent());

        MessageRouter router = new MessageRouter(messagingAgents);
        router.addMessagingAgent(new MetadataFetcher(torrentId, metadataBytes -> {
            if (torrentRegistry.getTorrent(torrentId).isPresent()) {
                return;
            }

            Torrent torrent = metadataService.fromByteArray(metadataBytes);
            TorrentDescriptor descriptor = torrentRegistry.register(torrent, params.getStorage());

            beginTorrentProcessing(torrent, descriptor, params, torrentWorker, session, router);
        }));

        torrentWorker.setPeerWorkerFactory(new PeerWorkerFactory(router));

        peerRegistry.addPeerConsumer(torrentId, session::onPeerDiscovered);
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

    private DataWorker createDataWorker(TorrentDescriptor descriptor) {
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }
}
