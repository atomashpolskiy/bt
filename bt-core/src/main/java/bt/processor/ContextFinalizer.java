package bt.processor;

/**
 * Used to finalize context and cleanup resources,
 * when processing completes normally or terminates abruptly due to error
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public interface ContextFinalizer<C extends ProcessingContext> {

    /**
     * Perform finalization and cleanup.
     *
     * @param context Processing context, that should be finalized
     * @since 1.5
     */
    void finalizeContext(C context);
}
