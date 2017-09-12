package bt.processor.torrent;

import bt.metainfo.Torrent;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;

public class FetchTorrentStage extends TerminateOnErrorProcessingStage<TorrentContext> {

    public FetchTorrentStage(ProcessingStage<TorrentContext> next) {
        super(next);
    }

    @Override
    protected void doExecute(TorrentContext context) {
        Torrent torrent = context.getTorrentSupplier().get();
        context.setTorrentId(torrent.getTorrentId());
        context.setTorrent(torrent);
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.TORRENT_FETCHED;
    }
}
