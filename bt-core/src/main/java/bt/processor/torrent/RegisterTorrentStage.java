package bt.processor.torrent;

import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.TorrentRegistry;

public class RegisterTorrentStage extends BaseProcessingStage<TorrentContext> {

    private TorrentRegistry torrentRegistry;

    public RegisterTorrentStage(ProcessingStage<TorrentContext> next,
                         TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(TorrentContext context) {
        torrentRegistry.register(context.getTorrent(), context.getStorage());
    }
}
