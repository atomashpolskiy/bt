package bt.processor;

import bt.processor.listener.ListenerSource;

public interface Processor<C extends ProcessingContext> {

    ProcessingFuture process(C context, ListenerSource<C> listenerSource);
}
