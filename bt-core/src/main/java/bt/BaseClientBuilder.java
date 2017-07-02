package bt;

import bt.data.Storage;
import bt.magnet.MagnetUri;
import bt.magnet.MagnetUriParser;
import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.module.ClientExecutor;
import bt.processor.ProcessingContext;
import bt.processor.ProcessingStage;
import bt.processor.ProcessorFactory;
import bt.processor.magnet.MagnetContext;
import bt.processor.torrent.TorrentContext;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.TorrentRegistry;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.RarestFirstSelector;
import bt.torrent.selector.SelectorAdapter;
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

    private Supplier<Torrent> torrentSupplier;
    private MagnetUri magnetUri;

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
        Objects.requireNonNull(torrentUrl, "Missing torrent file URL");
        this.torrentSupplier = () -> fetchTorrentFromUrl(torrentUrl);
        this.magnetUri = null;
        return (B) this;
    }

    private Torrent fetchTorrentFromUrl(URL metainfoUrl) {
        return getRuntime().service(IMetadataService.class).fromUrl(metainfoUrl);
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
        this.magnetUri = null;
        return (B) this;
    }

    /**
     * Set magnet URI in BEP-9 format
     *
     * @param magnetUri Magnet URI
     * @see MagnetUriParser
     * @since 1.3
     */
    @SuppressWarnings("unchecked")
    public B magnet(String magnetUri) {
        this.magnetUri = new MagnetUriParser().parse(magnetUri);
        this.torrentSupplier = null;
        return (B) this;
    }

    /**
     * Set magnet URI
     *
     * @param magnetUri Magnet URI
     * @see MagnetUriParser
     * @since 1.3
     */
    @SuppressWarnings("unchecked")
    public B magnet(MagnetUri magnetUri) {
        this.magnetUri = Objects.requireNonNull(magnetUri, "Missing magnet URI");
        this.torrentSupplier = null;
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
        Objects.requireNonNull(pieceSelectionStrategy, "Missing piece selection strategy");
        this.pieceSelector = new SelectorAdapter(pieceSelectionStrategy);
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
    protected abstract BtRuntime getRuntime();

    /**
     * @since 1.1
     */
    public BtClient build() {
        Objects.requireNonNull(storage, "Missing data storage");

        BtRuntime runtime = getRuntime();
        Objects.requireNonNull(runtime, "Missing runtime");

        Supplier<BtClient> clientSupplier;
        if (torrentSupplier != null) {
            clientSupplier = () -> buildClient(runtime, torrentSupplier);
        } else if (this.magnetUri != null) {
            clientSupplier = () -> buildClient(runtime, magnetUri);
        } else {
            throw new IllegalStateException("Missing torrent supplier, torrent URL or magnet URI");
        }

        return shouldInitEagerly ? clientSupplier.get() : new LazyClient(clientSupplier);
    }

    private BtClient buildClient(BtRuntime runtime, Supplier<Torrent> torrentSupplier) {
        TorrentContext context = new TorrentContext(pieceSelector, storage, torrentSupplier);

        return new RuntimeAwareClient(runtime, new DefaultClient<>(getExecutor(runtime),
                runtime.service(TorrentRegistry.class), processor(TorrentContext.class), context));
    }

    private BtClient buildClient(BtRuntime runtime, MagnetUri magnetUri) {
        // TODO: build session with MagnetUri instead of just torrent ID
        // in order to fully utilize display_name, bootstrap trackers and peers
        MagnetContext context = new MagnetContext(magnetUri, pieceSelector, storage);

        return new RuntimeAwareClient(runtime, new DefaultClient<>(getExecutor(runtime),
                runtime.service(TorrentRegistry.class), processor(MagnetContext.class), context));
    }

    private <C extends ProcessingContext> ProcessingStage<C> processor(Class<C> contextType) {
        ProcessingStage<C> processor = getRuntime().service(ProcessorFactory.class).processor(contextType);
        if (processor == null) {
            throw new IllegalStateException("No processors found for context type: " + contextType.getName());
        }
        return processor;
    }

    private ExecutorService getExecutor(BtRuntime runtime) {
        return runtime.getInjector().getExistingBinding(Key.get(ExecutorService.class, ClientExecutor.class))
                .getProvider().get();
    }
}
