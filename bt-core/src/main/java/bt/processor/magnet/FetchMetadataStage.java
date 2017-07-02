package bt.processor.magnet;

import bt.metainfo.IMetadataService;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.torrent.messaging.MetadataFetcher;

public class FetchMetadataStage extends BaseProcessingStage<MagnetContext> {

    private IMetadataService metadataService;

    public FetchMetadataStage(ProcessingStage<MagnetContext> next,
                              IMetadataService metadataService) {
        super(next);
        this.metadataService = metadataService;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        MetadataFetcher metadataFetcher = new MetadataFetcher(metadataService, context.getMagnetUri().getTorrentId());
        context.getRouter().registerMessagingAgent(metadataFetcher);

        // TODO: need to also receive Bitfields and Haves (without validation for the number of pieces...)

        metadataFetcher.waitForCompletion();
        context.getRouter().unregisterMessagingAgent(metadataFetcher);
    }
}
