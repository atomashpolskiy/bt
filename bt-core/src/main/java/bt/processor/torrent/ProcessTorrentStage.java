package bt.processor.torrent;

import bt.metainfo.TorrentId;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerAnnouncer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ProcessTorrentStage<C extends TorrentContext> extends BaseProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private ITrackerService trackerService;
    private ExecutorService executor;

    public ProcessTorrentStage(ProcessingStage<C> next,
                               TorrentRegistry torrentRegistry,
                               ITrackerService trackerService,
                               ExecutorService executor) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
        this.executor = executor;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId().get());

        TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, context.getTorrent().get());
        announcer.start();

        CompletableFuture.runAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                    if (context.getSession().get().getState().getPiecesRemaining() == 0) {
                        descriptor.complete();
                        announcer.complete();
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, executor).thenRunAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).join();

        // TODO: misbehaving... but currently no way to know if runtime automatic shutdown was disabled
        // previously this was called via BtRuntime -> BtClient -> TorrentDescriptor
//        announcer.stop();
        descriptor.stop();
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }
}
