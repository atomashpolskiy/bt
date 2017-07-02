package bt.processor.torrent;

import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.TorrentRegistry;

public class RegisterTorrentStage<C extends TorrentContext> extends BaseProcessingStage<C> {

    private TorrentRegistry torrentRegistry;

    public RegisterTorrentStage(ProcessingStage<C> next,
                                TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(C context) {
        torrentRegistry.register(context.getTorrent().get(), context.getStorage());
    }
}
