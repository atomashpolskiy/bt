package bt.processor;

/**
 * Base class for processing stage implementations.
 *
 * @param <C> Type of processing context
 * @since 1.3
 */
public abstract class RoutingProcessingStage<C extends ProcessingContext> implements ProcessingStage<C> {

    private final ProcessingStage<C> next;

    /**
     * @param next Default next processing stage
     * @since 1.3
     */
    public RoutingProcessingStage(ProcessingStage<C> next) {
        this.next = next;
    }

    @Override
    public ProcessingStage<C> execute(C context) {
        return doExecute(context, next);
    }

    /**
     * Execute current stage and calculate the next stage.
     *
     * In most cases the implementing class should just return the default next stage,
     * that is passed as the second argument to this method. However, in some cases it can override
     * the actual next stage, e.g. to switch to the exception path in case of a processing error.
     *
     * @param context Processing context
     * @param next Default next stage (usually statically configured in ProcessorFactory)
     * @return Actual next stage
     * @since 1.5
     */
    protected abstract ProcessingStage<C> doExecute(C context, ProcessingStage<C> next);
}
