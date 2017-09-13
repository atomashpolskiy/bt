package bt.processor;

/**
 * Builds processors for different context types.
 *
 * @since 1.5
 */
public interface ProcessorFactory {

    /**
     * Build a processor for a given context type.
     *
     * @param contextType Processing context type
     * @return Processor for a given context type or null, if this context type is not supported
     * @since 1.3
     */
    <C extends ProcessingContext> Processor<C> processor(Class<C> contextType);
}
