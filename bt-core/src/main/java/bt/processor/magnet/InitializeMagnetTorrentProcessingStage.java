package bt.processor.magnet;

import bt.data.Bitfield;
import bt.processor.ProcessingStage;
import bt.processor.torrent.InitializeTorrentProcessingStage;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.IDataWorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitializeMagnetTorrentProcessingStage extends InitializeTorrentProcessingStage<MagnetContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InitializeMagnetTorrentProcessingStage.class);

    public InitializeMagnetTorrentProcessingStage(ProcessingStage<MagnetContext> next,
                                                  TorrentRegistry torrentRegistry,
                                                  IDataWorkerFactory dataWorkerFactory,
                                                  Config config) {
        super(next, torrentRegistry, dataWorkerFactory, config);
    }

    @Override
    protected void doExecute(MagnetContext context) {
        super.doExecute(context);

        BitfieldBasedStatistics statistics = context.getPieceStatistics();
        // process bitfields and haves that we received while fetching metadata
        context.getBitfieldConsumer().getBitfields().forEach((peer, bitfieldBytes) -> {
            if (statistics.getPeerBitfield(peer).isPresent()) {
                // we should not have received peer's bitfields twice, but whatever.. ignore and continue
                return;
            }
            try {
                statistics.addBitfield(peer, new Bitfield(bitfieldBytes, statistics.getPiecesTotal()));
            } catch (Exception e) {
                LOGGER.warn("Error happened when processing peer's bitfield", e);
            }
        });
        context.getBitfieldConsumer().getHaves().forEach((peer, pieces) -> {
            try {
                pieces.forEach(piece -> statistics.addPiece(peer, piece));
            } catch (Exception e) {
                LOGGER.warn("Error happened when processing peer's haves", e);
            }
        });
        // unregistering only now, so that there were no gaps in bitifield receiving
        context.getRouter().unregisterMessagingAgent(context.getBitfieldConsumer());
        context.setBitfieldConsumer(null); // mark for gc collection
    }
}
