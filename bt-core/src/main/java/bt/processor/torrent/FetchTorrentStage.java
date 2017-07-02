package bt.processor.torrent;

import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;

public class FetchTorrentStage extends BaseProcessingStage<TorrentContext> {

    public FetchTorrentStage(ProcessingStage<TorrentContext> next) {
        super(next);
    }

    @Override
    protected void doExecute(TorrentContext context) {
        context.setTorrent(context.getTorrentSupplier().get());
    }
}
