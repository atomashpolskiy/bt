package bt.processor.magnet;

import bt.metainfo.IMetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.MetadataFetcher;

public class FetchMetadataStage extends BaseProcessingStage<MagnetContext> {

    private IMetadataService metadataService;
    private TorrentRegistry torrentRegistry;

    public FetchMetadataStage(ProcessingStage<MagnetContext> next,
                              IMetadataService metadataService,
                              TorrentRegistry torrentRegistry) {
        super(next);
        this.metadataService = metadataService;
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        TorrentId torrentId = context.getMagnetUri().getTorrentId();
        getDescriptor(torrentId).start();

        MetadataFetcher metadataFetcher = new MetadataFetcher(metadataService, torrentId);

        context.getRouter().registerMessagingAgent(metadataFetcher);

        // TODO: need to also receive Bitfields and Haves (without validation for the number of pieces...)

        Torrent torrent = metadataFetcher.fetchTorrent();
        context.setTorrent(torrent);

        context.getRouter().unregisterMessagingAgent(metadataFetcher);
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }
}
