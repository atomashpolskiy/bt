package bt.processor.torrent;

import bt.metainfo.Torrent;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;

import java.util.function.Supplier;

public class FetchTorrentStage extends BaseProcessingStage<TorrentContext> {

    private Supplier<Torrent> torrentSupplier;

    public FetchTorrentStage(ProcessingStage<TorrentContext> next, Supplier<Torrent> torrentSupplier) {
        super(next);
        this.torrentSupplier = torrentSupplier;
    }

    @Override
    protected void doExecute(TorrentContext context) {
        context.setTorrent(torrentSupplier.get());
    }
}
