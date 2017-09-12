package bt.processor;

import bt.processor.listener.ProcessingEvent;

/**
 * @param <C> Type of processing context
 * @since 1.3
 */
public interface ProcessingStage<C extends ProcessingContext> {

    /**
     * @return Type of event, that should be triggered after this stage has completed.
     * @since 1.5
     */
    ProcessingEvent after();

    /**
     * @param context Processing context
     * @return Next stage
     * @since 1.3
     */
    ProcessingStage<C> execute(C context);
}
