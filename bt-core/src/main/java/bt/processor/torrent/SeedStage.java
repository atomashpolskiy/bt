package bt.processor.torrent;

import bt.metainfo.TorrentId;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;

public class SeedStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private TorrentRegistry torrentRegistry;

    public SeedStage(ProcessingStage<C> next,
                     TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId().get());

        while (descriptor.isActive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted", e);
            }
        }
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
