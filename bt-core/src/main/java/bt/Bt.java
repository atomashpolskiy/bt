package bt;

import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.PieceSelectionStrategy;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.RarestFirstSelector;

import java.net.URL;
import java.util.Objects;
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
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
     */
    public static Bt client(Storage storage) {
        return new Bt(storage);
    }

    /**
     * Create a standalone client builder with a private runtime
     *
     * @since 1.1
     */
    public static StandaloneClientBuilder client() {
        return StandaloneClientBuilder.standalone();
    }

    /**
     * Create a standard client builder with the provided runtime
     *
     * @since 1.1
     */
    public static BtClientBuilder client(BtRuntime runtime) {
        return BtClientBuilder.runtime(runtime);
    }

    private Storage storage;

    private URL torrentUrl;
    private Supplier<Torrent> torrentSupplier;

    private PieceSelector pieceSelector;
    private PieceSelectionStrategy pieceSelectionStrategy;

    private boolean shouldInitEagerly;

    private Bt(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "Missing data storage");
        // set default piece selector
        this.pieceSelector = RarestFirstSelector.randomizedRarest();
    }

    /**
     * Set torrent metainfo URL
     *
     * @see #torrentSupplier(Supplier)
     * @since 1.0
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
     */
    public Bt url(URL metainfoUrl) {
        this.torrentUrl = Objects.requireNonNull(metainfoUrl, "Missing metainfo file URL");
        this.torrentSupplier = null;
        return this;
    }

    /**
     * Set custom torrent metainfo supplier
     *
     * @see #url(URL)
     * @since 1.0
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
     */
    public Bt torrentSupplier(Supplier<Torrent> torrentSupplier) {
        this.torrentSupplier = torrentSupplier;
        this.torrentUrl = null;
        return this;
    }

    /**
     * Set piece selection strategy
     *
     * @since 1.0
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
     */
    public Bt selector(PieceSelectionStrategy pieceSelectionStrategy) {
        this.pieceSelectionStrategy = Objects.requireNonNull(pieceSelectionStrategy, "Missing piece selection strategy");
        this.pieceSelector = null;
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
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
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
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
     */
    public BtClient attachToRuntime(BtRuntime runtime) {
        Objects.requireNonNull(runtime);
        return doBuild(runtime);
    }

    /**
     * Build standalone client within a private runtime
     *
     * @return Torrent client
     * @since 1.0
     * @deprecated since 1.1 in favor of {@link #client()} and {@link #client(BtRuntime)} builder methods
     */
    public BtClient standalone() {
        return doBuild(BtRuntime.defaultRuntime());
    }

    private BtClient doBuild(BtRuntime runtime) {
        BtClientBuilder clientBuilder = BtClientBuilder.runtime(runtime);

        clientBuilder.storage(storage);

        if (torrentUrl != null) {
            clientBuilder.torrent(torrentUrl);
        } else if (torrentSupplier != null) {
            clientBuilder.torrent(torrentSupplier);
        } else {
            throw new IllegalStateException("Missing torrent supplier or torrent URL");
        }

        if (pieceSelector != null) {
            clientBuilder.selector(pieceSelector);
        } else if (pieceSelectionStrategy != null) {
            clientBuilder.selector(pieceSelectionStrategy);
        } else {
            throw new IllegalStateException("Missing piece selection strategy");
        }

        if (shouldInitEagerly) {
            clientBuilder.initEagerly();
        }

        return clientBuilder.build();
    }
}
