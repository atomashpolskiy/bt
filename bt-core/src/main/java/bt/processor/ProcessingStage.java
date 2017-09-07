package bt.processor;

import bt.processor.listener.ProcessingEvent;

public interface ProcessingStage<C extends ProcessingContext> {

    ProcessingEvent after();

    ProcessingStage<C> execute(C context);
}
