package bt.processor.torrent;

import bt.processor.ContextFinalizer;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TrackerAnnouncer;

public class TorrentContextFinalizer<C extends TorrentContext> implements ContextFinalizer<C> {

    private TorrentRegistry torrentRegistry;

    public TorrentContextFinalizer(TorrentRegistry torrentRegistry) {
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    public void finish(TorrentContext context) {
        context.getTorrentId().ifPresent(torrentId -> {
            torrentRegistry.getDescriptor(torrentId).ifPresent(TorrentDescriptor::stop);
        });
        context.getAnnouncer().ifPresent(TrackerAnnouncer::stop);
    }

    @Override
    public void stop(TorrentContext context) {
        finish(context);
    }
}
