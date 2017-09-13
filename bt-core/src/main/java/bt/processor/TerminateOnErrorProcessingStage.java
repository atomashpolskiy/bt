package bt.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base processing stage, that terminates processing chain in case of error.
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public abstract class TerminateOnErrorProcessingStage<C extends ProcessingContext> extends RoutingProcessingStage<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminateOnErrorProcessingStage.class);

    public TerminateOnErrorProcessingStage(ProcessingStage<C> next) {
        super(next);
    }

    @Override
    protected final ProcessingStage<C> doExecute(C context, ProcessingStage<C> next) {
        try {
            doExecute(context);
        } catch (Exception e) {
            LOGGER.error("Unexpected error during processing, will finalize context and terminate...", e);
            next = null; // terminate processing chain
        }
        return next;
    }

    /**
     * Perform processing. Implementations are free to throw exceptions,
     * in which case the processing chain will be terminated.
     *
     * @since 1.5
     */
    protected abstract void doExecute(C context);
}
