package bt.processor.torrent;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import bt.tracker.TrackerAnnouncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ProcessTorrentStage<C extends TorrentContext> extends BaseProcessingStage<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTorrentStage.class);

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

        Torrent torrent = context.getTorrent().get();
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
        if (announceKey.isPresent()) {
            TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, torrent.getTorrentId(), announceKey.get());
            context.setAnnouncer(announcer);
        }

        start(context);

        CompletableFuture<Void> future;

        future = CompletableFuture.runAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                    if (context.getSession().get().getState().getPiecesRemaining() == 0) {
                        descriptor.complete();
                        return;
                    }
                } catch (InterruptedException e) {
                    finish(context);
                    throw new RuntimeException(e);
                }
            }
        }, executor);

        future = future.thenRunAsync(() -> {
            // might have been stopped externally before the torrent was actually completed
            if (context.getSession().get().getState().getPiecesRemaining() == 0) {
                complete(context);
            }
        }, executor);

        future = future.thenRunAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    finish(context);
                    throw new RuntimeException(e);
                }
            }
        }, executor);

        future.join();

        finish(context);
    }

    private void start(C context) {
        try {
            onStarted(context);
        } catch (Exception e) {
            LOGGER.error("Unexpected error", e);
        }
    }

    protected void onStarted(C context) {
        context.getAnnouncer().ifPresent(TrackerAnnouncer::start);
    }

    private void complete(C context) {
        try {
            onCompleted(context);
        } catch (Exception e) {
            LOGGER.error("Unexpected error", e);
        }
    }

    protected void onCompleted(C context) {
        context.getAnnouncer().ifPresent(TrackerAnnouncer::complete);
    }

    private void finish(C context) {
        try {
            onFinished(context);
        } catch (Exception e) {
            LOGGER.error("Unexpected error", e);
        }
    }

    protected void onFinished(C context) {
        // TODO: misbehaving... but currently no way to know if runtime automatic shutdown was disabled
        // previously this was called via BtRuntime -> BtClient -> TorrentDescriptor
//        announcer.stop();
        getDescriptor(context.getTorrentId().get()).stop();
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }
}
