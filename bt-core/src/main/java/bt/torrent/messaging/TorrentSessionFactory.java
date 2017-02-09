package bt.torrent.messaging;

import bt.metainfo.Torrent;
import bt.module.MessagingAgents;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.runtime.Config;
import bt.torrent.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.ITorrentSessionFactory;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.PieceSelector;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionParams;
import bt.torrent.data.DataWorker;
import bt.torrent.data.IDataWorkerFactory;
import com.google.inject.Inject;

import java.util.HashSet;
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
        PieceManager pieceManager = createPieceManager(
                descriptor.getDataDescriptor().getBitfield(), params.getSelectionStrategy());
        DataWorker dataWorker = createDataWorker(descriptor);
        IPeerWorkerFactory peerWorkerFactory = createPeerWorkerFactory(descriptor, pieceManager, dataWorker);

        TorrentSession session = new DefaultTorrentSession(connectionPool, pieceManager,
                messageDispatcher, peerWorkerFactory, torrent, config.getMaxPeerConnectionsPerTorrent());

        peerRegistry.addPeerConsumer(torrent, session::onPeerDiscovered);
        connectionPool.addConnectionListener(session);

        return session;
    }

    private TorrentDescriptor getTorrentDescriptor(Torrent torrent) {
        return torrentRegistry.getDescriptor(torrent)
                .orElseThrow(() -> new IllegalStateException("Unknown torrent: " + torrent));
    }

    private PieceManager createPieceManager(Bitfield bitfield, PieceSelectionStrategy selectionStrategy) {
        Assignments assignments = new Assignments();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield.getPiecesTotal());
        Predicate<Integer> validator = new IncompleteUnassignedPieceValidator(bitfield, assignments);

        PieceSelector selector = new SelectorAdapter(selectionStrategy, pieceStatistics, validator);
        return new PieceManager(bitfield, selector, assignments, pieceStatistics);
    }

    private DataWorker createDataWorker(TorrentDescriptor descriptor) {
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }

    private IPeerWorkerFactory createPeerWorkerFactory(TorrentDescriptor descriptor,
                                                       PieceManager pieceManager,
                                                       DataWorker dataWorker) {
        Set<Object> messagingAgents = new HashSet<>();
        messagingAgents.add(GenericConsumer.consumer());
        messagingAgents.add(new BitfieldConsumer(pieceManager));
        messagingAgents.add(new PeerRequestConsumer(dataWorker));
        messagingAgents.add(new RequestProducer(descriptor.getDataDescriptor().getChunkDescriptors(), pieceManager));
        messagingAgents.add(new PieceConsumer(pieceManager, dataWorker));

        messagingAgents.addAll(this.messagingAgents);

        return new PeerWorkerFactory(messagingAgents);
    }
}
