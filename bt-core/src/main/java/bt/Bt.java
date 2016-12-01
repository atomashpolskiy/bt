package bt;

import bt.data.Storage;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.module.ClientExecutor;
import bt.module.MessagingAgent;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.Config;
import bt.peer.IPeerRegistry;
import bt.torrent.TorrentRegistry;
import bt.torrent.DefaultTorrentSession;
import bt.torrent.IPieceManager;
import bt.torrent.ITorrentDescriptor;
import bt.torrent.PieceManager;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.RarestFirstSelectionStrategy;
import bt.torrent.TorrentSession;
import bt.torrent.data.IDataWorker;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.messaging.BitfieldConsumer;
import bt.torrent.messaging.GenericConsumer;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.PeerWorkerFactory;
import bt.torrent.messaging.PieceConsumer;
import bt.torrent.messaging.PeerRequestProcessor;
import bt.torrent.messaging.RequestProducer;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Torrent client builder
 *
 * @since 1.0
 */
public class Bt {

    /**
     * Build torrent client with provided storage as the data back-end
     *
     * @param storage Storage, that will be used as the data back-end
     * @return Client builder
     * @since 1.0
     */
    public static Bt client(Storage storage) {
        return new Bt(storage);
    }

    private BtRuntime runtime;

    private Supplier<Torrent> torrentSupplier;
    private PieceSelectionStrategy pieceSelectionStrategy;
    private boolean shouldInitEagerly;
    private Storage storage;

    private Bt(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "Missing data storage");
        // set default piece selector
        this.pieceSelectionStrategy = RarestFirstSelectionStrategy.randomizedRarest();
    }

    /**
     * Set torrent metainfo URL
     *
     * @see #torrentSupplier(Supplier)
     * @since 1.0
     */
    public Bt url(URL metainfoUrl) {
        Objects.requireNonNull(metainfoUrl, "Missing metainfo file URL");
        this.torrentSupplier = () -> fetchTorrentFromUrl(metainfoUrl);
        return this;
    }

    /**
     * Set custom torrent metainfo supplier
     *
     * @see #url(URL)
     * @since 1.0
     */
    public Bt torrentSupplier(Supplier<Torrent> torrentSupplier) {
        this.torrentSupplier = torrentSupplier;
        return this;
    }

    /**
     * Set piece selection strategy
     *
     * @since 1.0
     */
    public Bt selector(PieceSelectionStrategy pieceSelectionStrategy) {
        this.pieceSelectionStrategy = pieceSelectionStrategy;
        return this;
    }

    /**
     * Initialize the client eagerly.
     *
     * By default the client is initialized lazily
     * upon calling {@link BtClient#startAsync()} method or one of its' overloaded version.
     *
     * Initialization is implementation-specific and may include fetching torrent metainfo,
     * creating torrent and data descriptors, reserving storage space,
     * instantiating client-specific services, triggering DI injection, etc.
     *
     * @since 1.0
     */
    public Bt initEagerly() {
        shouldInitEagerly = true;
        return this;
    }

    /**
     * Build client and attach it to the provided runtime.
     *
     * @return Torrent client
     * @since 1.0
     */
    public BtClient attachToRuntime(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        return doBuild();
    }

    /**
     * Build standalone client within a private runtime
     *
     * @return Torrent client
     * @since 1.0
     */
    public BtClient standalone() {
        this.runtime = BtRuntime.defaultRuntime();
        return doBuild();
    }

    private BtClient doBuild() {

        Objects.requireNonNull(torrentSupplier, "Missing torrent supplier");
        Objects.requireNonNull(pieceSelectionStrategy, "Missing piece selection strategy");

        return shouldInitEagerly ? createClient() : new LazyBtClient(this::createClient);
    }

    private BtClient createClient() {

        Torrent torrent = torrentSupplier.get();
        ITorrentDescriptor descriptor = getTorrentDescriptor(torrent);
        IDataWorker dataWorker = createDataWorker(descriptor);

        TorrentSession session = createSession(torrent, descriptor, dataWorker);

        return new RuntimeAwareBtClient(runtime,
                new DefaultBtClient(getExecutor(), descriptor, session));
    }

    private Torrent fetchTorrentFromUrl(URL metainfoUrl) {
        IMetadataService metadataService = runtime.service(IMetadataService.class);
        return metadataService.fromUrl(metainfoUrl);
    }

    private ITorrentDescriptor getTorrentDescriptor(Torrent torrent) {
        TorrentRegistry torrentRegistry = runtime.service(TorrentRegistry.class);
        return torrentRegistry.getOrCreateDescriptor(torrent, storage);
    }

    private IDataWorker createDataWorker(ITorrentDescriptor descriptor) {
        IDataWorkerFactory dataWorkerFactory = runtime.service(IDataWorkerFactory.class);
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }

    private TorrentSession createSession(Torrent torrent, ITorrentDescriptor descriptor, IDataWorker dataWorker) {

        IPeerConnectionPool connectionPool = runtime.service(IPeerConnectionPool.class);
        IMessageDispatcher messageDispatcher = runtime.service(IMessageDispatcher.class);

        PieceManager pieceManager = new PieceManager(descriptor.getDataDescriptor().getBitfield(), pieceSelectionStrategy);
        IPeerWorkerFactory peerWorkerFactory = createPeerWorkerFactory(descriptor, pieceManager, dataWorker, runtime.getConfig());

        DefaultTorrentSession session = new DefaultTorrentSession(connectionPool, pieceManager,
                messageDispatcher, peerWorkerFactory, torrent, runtime.getConfig().getMaxPeerConnectionsPerTorrent());

        IPeerRegistry peerRegistry = runtime.service(IPeerRegistry.class);
        peerRegistry.addPeerConsumer(torrent, session::onPeerDiscovered);

        connectionPool.addConnectionListener(session);

        return session;
    }

    private IPeerWorkerFactory createPeerWorkerFactory(ITorrentDescriptor descriptor, IPieceManager pieceManager,
                                                       IDataWorker dataWorker, Config config) {

        Set<Object> messagingAgents = new HashSet<>();
        messagingAgents.add(GenericConsumer.consumer());
        messagingAgents.add(new BitfieldConsumer(pieceManager));
        messagingAgents.add(new PeerRequestProcessor(dataWorker));
        messagingAgents.add(new RequestProducer(descriptor.getDataDescriptor().getChunkDescriptors(),
                pieceManager, config.getTransferBlockSize()));
        messagingAgents.add(new PieceConsumer(pieceManager, dataWorker));

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
