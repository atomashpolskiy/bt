package bt.processor.torrent;

import bt.metainfo.IMetadataService;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.messaging.MetadataFetcher;

public class FetchMetadataStage extends BaseProcessingStage<TorrentContext> {

    private IMetadataService metadataService;

    public FetchMetadataStage(ProcessingStage<TorrentContext> next,
                              IMetadataService metadataService) {
        super(next);
        this.metadataService = metadataService;
    }

    @Override
    protected void doExecute(TorrentContext context) {
        MetadataFetcher metadataFetcher = new MetadataFetcher(metadataService, context.getTorrentId());
        context.getSession().registerMessagingAgent(metadataFetcher);

        // TODO: need to also receive Bitfields and Haves (without validation for the number of pieces...)

        metadataFetcher.waitForCompletion();
        context.getSession().unregisterMessagingAgent(metadataFetcher);
    }
}
