package bt.processor;

import bt.processor.listener.ListenerSource;
import bt.processor.listener.ProcessingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

public class ChainProcessor<C extends ProcessingContext> implements Processor<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChainProcessor.class);

    private ProcessingStage<C> chainHead;
    private ContextFinalizer<C> finalizer;
    private ExecutorService executor;

    public ChainProcessor(ProcessingStage<C> chainHead,
                          ContextFinalizer<C> finalizer,
                          ExecutorService executor) {
        this.chainHead = chainHead;
        this.finalizer = finalizer;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<?> process(C context, ListenerSource<C> listenerSource) {
        return CompletableFuture.runAsync(() -> process(chainHead, context, listenerSource), executor);
    }

    public void process(ProcessingStage<C> chainHead,
                        C context,
                        ListenerSource<C> listenerSource) {
        try {
            executeStage(chainHead, context, listenerSource);
        } catch (Exception e) {
            LOGGER.error("Processing failed with error", e);
        } finally {
            complete(context);
        }
    }

    private void executeStage(ProcessingStage<C> chainHead,
                              C context,
                              ListenerSource<C> listenerSource) {
        ProcessingEvent stageFinished = chainHead.after();
        Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> listeners;
        if (stageFinished != null) {
            listeners = listenerSource.getListeners(stageFinished);
        } else {
            listeners = Collections.emptyList();
        }

        ProcessingStage<C> next = doExecute(chainHead, context, listeners);
        if (next != null) {
            executeStage(next, context, listenerSource);
        }
    }

    private ProcessingStage<C> doExecute(ProcessingStage<C> stage,
                                         C context,
                                         Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> listeners) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Processing next stage: torrent ID (%s), stage (%s)",
                    context.getTorrentId().orElse(null), stage.getClass().getName()));
        }
        try {
            ProcessingStage<C> next = stage.execute(context);
            for (BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener : listeners) {
                try {
                    next = listener.apply(context, next);
                } catch (Exception e) {
                    LOGGER.error("Listener invocation failed", e);
                }
            }
            return next;
        } catch (Throwable e) {
            LOGGER.error(String.format("Processing failed with error: torrent ID (%s), stage (%s)",
                    context.getTorrentId().orElse(null), stage.getClass().getName()), e);
            throw e;
        }
    }

    private void complete(C context) {
        try {
            finalizer.finish(context);
        } catch (Exception e) {
            LOGGER.error("Failed to finalize context", e);
        }
    }
}
