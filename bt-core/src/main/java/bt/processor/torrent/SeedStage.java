package bt.processor.torrent;

import bt.metainfo.TorrentId;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class SeedStage<C extends TorrentContext> extends BaseProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private ExecutorService executor;

    public SeedStage(ProcessingStage<C> next,
                     TorrentRegistry torrentRegistry,
                     ExecutorService executor) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.executor = executor;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId().get());

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, executor);

        future.join();
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
