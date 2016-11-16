package bt;

import bt.data.DataAccessFactory;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.module.ClientExecutor;
import bt.module.MessagingAgent;
import bt.net.IConnectionHandlerFactory;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.DefaultBtClient;
import bt.runtime.LazyBtClient;
import bt.runtime.RuntimeAwareBtClient;
import bt.service.IConfigurationService;
import bt.service.IPeerRegistry;
import bt.service.ITorrentRegistry;
import bt.torrent.DefaultTorrentSession;
import bt.torrent.IPieceManager;
import bt.torrent.TorrentSession;
import bt.torrent.data.IDataWorker;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.PieceManager;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.RarestFirstSelectionStrategy;
import bt.torrent.messaging.BitfieldConsumer;
import bt.torrent.messaging.GenericConsumer;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.PeerWorkerFactory;
import bt.torrent.messaging.PieceConsumer;
import bt.torrent.messaging.RequestConsumer;
import bt.torrent.messaging.RequestProducer;
import bt.torrent.messaging.PieceProducer;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class Bt {

    public static Bt client(BtRuntime runtime) {
        return new Bt(runtime);
    }

    private BtRuntime runtime;

    private URL metainfoUrl;
    private PieceSelectionStrategy pieceSelectionStrategy;
    private boolean eagerInit;
    private DataAccessFactory dataAccessFactory;

    private Bt(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        pieceSelectionStrategy = RarestFirstSelectionStrategy.randomized();
    }

    public Bt url(URL metainfoUrl) {
        this.metainfoUrl = metainfoUrl;
        return this;
    }

    public Bt selector(PieceSelectionStrategy pieceSelectionStrategy) {
        this.pieceSelectionStrategy = pieceSelectionStrategy;
        return this;
    }

    public Bt eagerInit() {
        eagerInit = true;
        return this;
    }

    public BtClient build(DataAccessFactory dataAccessFactory) {

        Objects.requireNonNull(metainfoUrl, "Missing metainfo file URL");
        Objects.requireNonNull(pieceSelectionStrategy, "Missing piece selection strategy");

        this.dataAccessFactory = Objects.requireNonNull(dataAccessFactory, "Missing data access factory");

        BtClient client = eagerInit ? createClient() : new LazyBtClient(this::createClient);
        runtime.registerClient(client);
        return client;
    }

    private BtClient createClient() {

        Torrent torrent = getTorrent();
        ITorrentDescriptor descriptor = getTorrentDescriptor(torrent);
        IDataWorker dataWorker = createDataWorker(descriptor);

        TorrentSession session = createSession(torrent, descriptor, dataWorker);

        return new RuntimeAwareBtClient(runtime,
                new DefaultBtClient(getExecutor(), descriptor, session, dataWorker));
    }

    private Torrent getTorrent() {
        IMetadataService metadataService = runtime.service(IMetadataService.class);
        return metadataService.fromUrl(metainfoUrl);
    }

    private ITorrentDescriptor getTorrentDescriptor(Torrent torrent) {
        ITorrentRegistry torrentRegistry = runtime.service(ITorrentRegistry.class);
        return torrentRegistry.getOrCreateDescriptor(torrent, dataAccessFactory);
    }

    private IDataWorker createDataWorker(ITorrentDescriptor descriptor) {
        IDataWorkerFactory dataWorkerFactory = runtime.service(IDataWorkerFactory.class);
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }

    private TorrentSession createSession(Torrent torrent, ITorrentDescriptor descriptor, IDataWorker dataWorker) {

        IPeerConnectionPool connectionPool = runtime.service(IPeerConnectionPool.class);
        IConfigurationService configurationService = runtime.service(IConfigurationService.class);
        IConnectionHandlerFactory connectionHandlerFactory = runtime.service(IConnectionHandlerFactory.class);
        IMessageDispatcher messageDispatcher = runtime.service(IMessageDispatcher.class);

        PieceManager pieceManager = new PieceManager(descriptor.getDataDescriptor().getBitfield(), pieceSelectionStrategy);
        IPeerWorkerFactory peerWorkerFactory = createPeerWorkerFactory(descriptor, pieceManager, dataWorker);

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, configurationService,
                connectionHandlerFactory, pieceManager, messageDispatcher, peerWorkerFactory, torrent);

        dataWorker.addVerifiedPieceListener(session::onPieceVerified);

        IPeerRegistry peerRegistry = runtime.service(IPeerRegistry.class);
        peerRegistry.addPeerConsumer(torrent, session::onPeerDiscovered);

        connectionPool.addConnectionListener(session);

        return session;
    }

    private IPeerWorkerFactory createPeerWorkerFactory(ITorrentDescriptor descriptor, IPieceManager pieceManager,
                                                       IDataWorker dataWorker) {

        Set<Object> messagingAgents = new HashSet<>();
        messagingAgents.add(GenericConsumer.consumer());
        messagingAgents.add(new BitfieldConsumer(pieceManager));
        messagingAgents.add(new RequestConsumer(dataWorker));
        messagingAgents.add(new PieceProducer(dataWorker));
        messagingAgents.add(new RequestProducer(descriptor.getDataDescriptor().getChunkDescriptors(), pieceManager));
        messagingAgents.add(new PieceConsumer(dataWorker));

        Binding<Set<Object>> extraMessagingAgents = runtime.getInjector()
                .getExistingBinding(Key.get(new TypeLiteral<Set<Object>>(){}, MessagingAgent.class));
        if (extraMessagingAgents != null) {
            messagingAgents.addAll(extraMessagingAgents.getProvider().get());
        }

        return new PeerWorkerFactory(messagingAgents);
    }

    private ExecutorService getExecutor() {
        return runtime.getInjector().getExistingBinding(Key.get(ExecutorService.class, ClientExecutor.class))
                .getProvider().get();
    }
}
