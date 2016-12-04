package bt;

import bt.data.Storage;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.module.ClientExecutor;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.ITorrentSessionFactory;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.RarestFirstSelectionStrategy;
import bt.torrent.TorrentSessionParams;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import com.google.inject.Key;

import java.net.URL;
import java.util.Objects;
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

        return shouldInitEagerly ? createClient() : new LazyClient(this::createClient);
    }

    private BtClient createClient() {
        Torrent torrent = torrentSupplier.get();
        TorrentDescriptor descriptor = getTorrentDescriptor(torrent);

        ITorrentSessionFactory torrentSessionFactory = runtime.service(ITorrentSessionFactory.class);
        TorrentSession session = torrentSessionFactory.createSession(torrent, getSessionParams());

        return new RuntimeAwareClient(runtime,
                new DefaultClient(getExecutor(), descriptor, session));
    }

    private Torrent fetchTorrentFromUrl(URL metainfoUrl) {
        IMetadataService metadataService = runtime.service(IMetadataService.class);
        return metadataService.fromUrl(metainfoUrl);
    }

    private TorrentDescriptor getTorrentDescriptor(Torrent torrent) {
        TorrentRegistry torrentRegistry = runtime.service(TorrentRegistry.class);
        return torrentRegistry.getOrCreateDescriptor(torrent, storage);
    }

    private TorrentSessionParams getSessionParams() {
        TorrentSessionParams params = new TorrentSessionParams();
        params.setSelectionStrategy(pieceSelectionStrategy);
        return params;
    }

    private ExecutorService getExecutor() {
        return runtime.getInjector().getExistingBinding(Key.get(ExecutorService.class, ClientExecutor.class))
                .getProvider().get();
    }
}
