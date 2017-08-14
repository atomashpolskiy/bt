package bt;

import bt.module.ClientExecutor;
import bt.processor.ProcessingContext;
import bt.processor.ProcessingStage;
import bt.processor.ProcessorFactory;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.torrent.TorrentRegistry;
import com.google.inject.Key;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Provides basic capabilities to build a Bt client.
 *
 * @since 1.1
 */
public abstract class BaseClientBuilder<B extends BaseClientBuilder> {

    private BtRuntime runtime;
    private boolean shouldInitEagerly;

    /**
     * @since 1.1
     */
    protected BaseClientBuilder() {
    }

    /**
     * Set the runtime that the newly built client will be attached to.
     *
     * @param runtime Bt runtime
     * @since 1.4
     */
    @SuppressWarnings("unchecked")
    public B runtime(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Missing runtime");
        return (B) this;
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
        Objects.requireNonNull(runtime, "Missing runtime");
        Supplier<BtClient> clientSupplier = () -> buildClient(runtime, buildProcessingContext(runtime));
        return shouldInitEagerly ? clientSupplier.get() : new LazyClient(clientSupplier);
    }

    /**
     * @since 1.4
     */
    protected abstract ProcessingContext buildProcessingContext(BtRuntime runtime);

    private <C extends ProcessingContext> BtClient buildClient(BtRuntime runtime, C context) {
        @SuppressWarnings("unchecked")
        Class<C> contextType = (Class<C>) context.getClass();

        BtClient client = new DefaultClient<>(
                getExecutor(runtime),
                runtime.service(TorrentRegistry.class),
                processor(runtime, contextType),
                context);

        return new RuntimeAwareClient(runtime, client);
    }

    private <C extends ProcessingContext> ProcessingStage<C> processor(BtRuntime runtime, Class<C> contextType) {
        ProcessingStage<C> processor = runtime.service(ProcessorFactory.class).processor(contextType);
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
