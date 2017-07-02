package bt.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChainProcessor.class);

    public static <C extends ProcessingContext> void execute(ProcessingStage<C> chainHead, C context) {
        ProcessingStage<C> next = doExecute(chainHead, context);
        if (next != null) {
            execute(next, context);
        }
    }

    private static <C extends ProcessingContext> ProcessingStage<C> doExecute(ProcessingStage<C> stage, C context) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("Processing next stage: torrent ID (%s), stage (%s)",
                    context.getTorrentId(), stage.getClass().getName()));
        }
        try {
            return stage.execute(context);
        } catch (Throwable e) {
            LOGGER.error(String.format("Processing failed with error: torrent ID (%s), stage (%s)",
                    context.getTorrentId(), stage.getClass().getName()), e);
            throw e;
        }
    }
}
