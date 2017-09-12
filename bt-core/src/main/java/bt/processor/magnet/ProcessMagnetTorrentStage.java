package bt.processor.magnet;

import bt.processor.ProcessingStage;
import bt.processor.torrent.ProcessTorrentStage;
import bt.torrent.TorrentRegistry;
import bt.tracker.ITrackerService;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage<MagnetContext> {

    public ProcessMagnetTorrentStage(ProcessingStage<MagnetContext> next,
                                     TorrentRegistry torrentRegistry,
                                     ITrackerService trackerService) {
        super(next, torrentRegistry, trackerService);
    }

    @Override
    protected void onStarted(MagnetContext context) {
        // do not announce start, as it should have been done already per FetchMetadataStage
    }
}
