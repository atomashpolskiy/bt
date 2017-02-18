package bt;

import bt.data.Storage;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.module.ClientExecutor;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.ITorrentSessionFactory;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TorrentSession;
import bt.torrent.TorrentSessionParams;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.RarestFirstSelector;
import bt.torrent.selector.SequentialSelector;
import com.google.inject.Key;

import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Provides basic capabilities to build a Bt client.
 *
 * @since 1.1
 */
public abstract class BaseClientBuilder<B extends BaseClientBuilder> {

    private Storage storage;

    private URL torrentUrl;
    private Supplier<Torrent> torrentSupplier;

    private PieceSelectionStrategy pieceSelectionStrategy;
    private PieceSelector pieceSelector;

    private boolean shouldInitEagerly;

    /**
     * @since 1.1
     */
    protected BaseClientBuilder() {
        // set default piece selector
        this.pieceSelector = RarestFirstSelector.randomizedRarest();
    }

    /**
     * Set the provided storage as the data back-end
     *
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B storage(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "Missing data storage");
        return (B) this;
    }

    /**
     * Set torrent file URL
     *
     * @see #torrent(Supplier)
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B torrent(URL torrentUrl) {
        this.torrentUrl = Objects.requireNonNull(torrentUrl, "Missing torrent file URL");
        this.torrentSupplier = null;
        return (B) this;
    }

    /**
     * Set custom torrent file supplier
     *
     * @see #torrent(URL)
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B torrent(Supplier<Torrent> torrentSupplier) {
        this.torrentSupplier = Objects.requireNonNull(torrentSupplier, "Missing torrent supplier");
        this.torrentUrl = null;
        return (B) this;
    }

    /**
     * Set piece selection strategy
     *
     * @see #selector(PieceSelector)
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B selector(PieceSelectionStrategy pieceSelectionStrategy) {
        this.pieceSelectionStrategy = Objects.requireNonNull(pieceSelectionStrategy, "Missing piece selection strategy");
        this.pieceSelector = null;
        return (B) this;
    }

    /**
     * Set piece selection strategy
     *
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B selector(PieceSelector pieceSelector) {
        this.pieceSelector = Objects.requireNonNull(pieceSelector, "Missing piece selector");
        this.pieceSelectionStrategy = null;
        return (B) this;
    }

    /**
     * Use sequential piece selection strategy
     *
     * @since 1.1
     */
    public B sequentialSelector() {
       return selector(SequentialSelector.sequential());
    }

    /**
     * Use rarest first piece selection strategy
     *
     * @since 1.1
     */
    public B rarestSelector() {
       return selector(RarestFirstSelector.rarest());
    }

    /**
     * Use rarest first piece selection strategy
     *
     * @since 1.1
     */
    public B randomizedRarestSelector() {
       return selector(RarestFirstSelector.randomizedRarest());
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
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public B initEagerly() {
        this.shouldInitEagerly = true;
        return (B) this;
    }

    /**
     * @since 1.1
     */
    public BtClient build() {
        Objects.requireNonNull(storage, "Missing data storage");

        BtRuntime runtime = getRuntime();
        Objects.requireNonNull(runtime, "Missing runtime");

        Supplier<Torrent> torrentSupplier;
        if (torrentUrl == null) {
            torrentSupplier = Objects.requireNonNull(this.torrentSupplier, "Missing torrent supplier or torrent URL");
        } else {
            torrentSupplier = () -> fetchTorrentFromUrl(runtime, torrentUrl);
        }

        Supplier<BtClient> clientSupplier = () -> buildClient(runtime, torrentSupplier);
        return shouldInitEagerly ? clientSupplier.get() : new LazyClient(clientSupplier);
    }

    /**
     * @since 1.1
     */
    protected abstract BtRuntime getRuntime();

    private BtClient buildClient(BtRuntime runtime, Supplier<Torrent> torrentSupplier) {
        Torrent torrent = torrentSupplier.get();
        TorrentDescriptor descriptor = getTorrentDescriptor(runtime, torrent);

        ITorrentSessionFactory torrentSessionFactory = runtime.service(ITorrentSessionFactory.class);
        TorrentSession session = torrentSessionFactory.createSession(torrent, getSessionParams());

        return new RuntimeAwareClient(runtime, new DefaultClient(getExecutor(runtime), descriptor, session));
    }

    private Torrent fetchTorrentFromUrl(BtRuntime runtime, URL metainfoUrl) {
        IMetadataService metadataService = runtime.service(IMetadataService.class);
        return metadataService.fromUrl(metainfoUrl);
    }

    private TorrentDescriptor getTorrentDescriptor(BtRuntime runtime, Torrent torrent) {
        TorrentRegistry torrentRegistry = runtime.service(TorrentRegistry.class);
        return torrentRegistry.getOrCreateDescriptor(torrent, storage);
    }

    private TorrentSessionParams getSessionParams() {
        TorrentSessionParams params = new TorrentSessionParams();
        if (pieceSelector != null) {
            params.setPieceSelector(pieceSelector);
        } else {
            params.setSelectionStrategy(pieceSelectionStrategy);
        }
        return params;
    }

    private ExecutorService getExecutor(BtRuntime runtime) {
        return runtime.getInjector().getExistingBinding(Key.get(ExecutorService.class, ClientExecutor.class))
                .getProvider().get();
    }
}
