package bt.processor.magnet;

import bt.processor.ProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.processor.torrent.ProcessTorrentStage;
import bt.torrent.TorrentRegistry;
import bt.tracker.ITrackerService;

import java.util.concurrent.ExecutorService;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage<MagnetContext> {

    public ProcessMagnetTorrentStage(ProcessingStage<MagnetContext> next,
                                     TorrentRegistry torrentRegistry,
                                     ITrackerService trackerService,
                                     ExecutorService executor) {
        super(next, torrentRegistry, trackerService, executor);
    }

    @Override
    protected void onStarted(MagnetContext context) {
        // do not announce start, as it should have been done already per FetchMetadataStage
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
