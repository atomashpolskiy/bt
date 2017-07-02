package bt.processor.torrent;

import bt.data.Bitfield;
import bt.metainfo.TorrentId;
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
import bt.tracker.ITrackerService;
import bt.tracker.TrackerAnnouncer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class ProcessTorrentStage<C extends TorrentContext> extends BaseProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private IDataWorkerFactory dataWorkerFactory;
    private ITrackerService trackerService;
    private ExecutorService executor;
    private Config config;

    public ProcessTorrentStage(ProcessingStage<C> next,
                               TorrentRegistry torrentRegistry,
                               IDataWorkerFactory dataWorkerFactory,
                               ITrackerService trackerService,
                               ExecutorService executor,
                               Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
        this.trackerService = trackerService;
        this.executor = executor;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId().get());
        descriptor.start();

        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield);
        PieceSelector selector = createSelector(context.getPieceSelector(), bitfield);

        DataWorker dataWorker = createDataWorker(descriptor);
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);

        context.getRouter().registerMessagingAgent(GenericConsumer.consumer());
        context.getRouter().registerMessagingAgent(new BitfieldConsumer(bitfield, pieceStatistics));
        context.getRouter().registerMessagingAgent(new PieceConsumer(bitfield, dataWorker));
        context.getRouter().registerMessagingAgent(new PeerRequestConsumer(dataWorker));
        context.getRouter().registerMessagingAgent(new RequestProducer(descriptor.getDataDescriptor()));

        context.setBitfield(bitfield);
        context.setAssignments(assignments);
        context.setPieceStatistics(pieceStatistics);

        TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, context.getTorrent().get());
        announcer.start();

        CompletableFuture.runAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                    if (context.getSession().get().getState().getPiecesRemaining() == 0) {
                        descriptor.complete();
                        announcer.complete();
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, executor).thenRunAsync(() -> {
            while (descriptor.isActive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).join();

        // TODO: misbehaving... but currently no way to know if runtime automatic shutdown was disabled
        // previously this was called via BtRuntime -> BtClient -> TorrentDescriptor
//        announcer.stop();
        descriptor.stop();
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
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
