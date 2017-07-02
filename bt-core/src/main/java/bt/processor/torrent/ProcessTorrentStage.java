package bt.processor.torrent;

import bt.data.Bitfield;
import bt.processor.BaseProcessingStage;
import bt.processor.ProcessingStage;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.DataWorker;
import bt.torrent.data.IDataWorkerFactory;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.BitfieldConsumer;
import bt.torrent.messaging.GenericConsumer;
import bt.torrent.messaging.IncompletePiecesValidator;
import bt.torrent.messaging.PeerRequestConsumer;
import bt.torrent.messaging.PieceConsumer;
import bt.torrent.messaging.RequestProducer;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.ValidatingSelector;

import java.util.function.Predicate;

public class ProcessTorrentStage extends BaseProcessingStage<TorrentContext> {

    private TorrentRegistry torrentRegistry;
    private IDataWorkerFactory dataWorkerFactory;
    private Config config;

    public ProcessTorrentStage(ProcessingStage<TorrentContext> next,
                        TorrentRegistry torrentRegistry,
                        IDataWorkerFactory dataWorkerFactory,
                        Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
        this.config = config;
    }

    @Override
    protected void doExecute(TorrentContext context) {
        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(context.getTorrentId())
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + context.getTorrentId()));

        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield);
        PieceSelector selector = createSelector(context.getPieceSelector(), bitfield);

        DataWorker dataWorker = createDataWorker(descriptor);
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);

        context.getSession().registerMessagingAgent(GenericConsumer.consumer());
        context.getSession().registerMessagingAgent(new BitfieldConsumer(bitfield, pieceStatistics));
        context.getSession().registerMessagingAgent(new PieceConsumer(bitfield, dataWorker));
        context.getSession().registerMessagingAgent(new PeerRequestConsumer(dataWorker));
        context.getSession().registerMessagingAgent(new RequestProducer(descriptor.getDataDescriptor()));

        context.getTorrentWorker().setBitfield(bitfield);
        context.getTorrentWorker().setAssignments(assignments);
        context.getTorrentWorker().setPieceStatistics(pieceStatistics);
    }

    private PieceSelector createSelector(PieceSelector selector,
                                         Bitfield bitfield) {
        Predicate<Integer> validator = new IncompletePiecesValidator(bitfield);
        return new ValidatingSelector(validator, selector);
    }

    private DataWorker createDataWorker(TorrentDescriptor descriptor) {
        return dataWorkerFactory.createWorker(descriptor.getDataDescriptor());
    }
}
