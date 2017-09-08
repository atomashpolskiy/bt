package bt.processor;

import bt.processor.listener.ListenerSource;

import java.util.concurrent.CompletableFuture;

public interface Processor<C extends ProcessingContext> {

    CompletableFuture<?> process(C context, ListenerSource<C> listenerSource);
}
