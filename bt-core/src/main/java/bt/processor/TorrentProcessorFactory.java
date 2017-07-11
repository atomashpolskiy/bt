package bt.processor;

import bt.metainfo.IMetadataService;
import bt.module.ClientExecutor;
import bt.module.MessagingAgents;
import bt.net.IMessageDispatcher;
import bt.net.IPeerConnectionPool;
import bt.peer.IPeerRegistry;
import bt.processor.magnet.FetchMetadataStage;
import bt.processor.magnet.InitializeMagnetTorrentProcessingStage;
import bt.processor.magnet.MagnetContext;
import bt.processor.magnet.ProcessMagnetTorrentStage;
import bt.processor.torrent.CreateSessionStage;
import bt.processor.torrent.FetchTorrentStage;
import bt.processor.torrent.InitializeTorrentProcessingStage;
import bt.processor.torrent.ProcessTorrentStage;
import bt.processor.torrent.TorrentContext;
import bt.runtime.Config;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.IDataWorkerFactory;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class TorrentProcessorFactory implements ProcessorFactory {

    private TorrentRegistry torrentRegistry;
    private IDataWorkerFactory dataWorkerFactory;
    private ITrackerService trackerService;
    private ExecutorService executor;
    private IPeerRegistry peerRegistry;
    private IPeerConnectionPool connectionPool;
    private IMessageDispatcher messageDispatcher;
    private Set<Object> messagingAgents;
    private IMetadataService metadataService;
    private Config config;

    private final Map<Class<?>, ProcessingStage<?>> processors;

    @Inject
    public TorrentProcessorFactory(TorrentRegistry torrentRegistry,
                                   IDataWorkerFactory dataWorkerFactory,
                                   ITrackerService trackerService,
                                   @ClientExecutor ExecutorService executor,
                                   IPeerRegistry peerRegistry,
                                   IPeerConnectionPool connectionPool,
                                   IMessageDispatcher messageDispatcher,
                                   @MessagingAgents Set<Object> messagingAgents,
                                   IMetadataService metadataService,
                                   Config config) {
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
        this.trackerService = trackerService;
        this.executor = executor;
        this.peerRegistry = peerRegistry;
        this.connectionPool = connectionPool;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
        this.metadataService = metadataService;
        this.config = config;

        this.processors = processors();
    }

    private Map<Class<?>, ProcessingStage<?>> processors() {
        Map<Class<?>, ProcessingStage<?>> processors = new HashMap<>();

        processors.put(TorrentContext.class, createTorrentProcessor());
        processors.put(MagnetContext.class, createMagnetProcessor());

        return processors;
    }

    private ProcessingStage<TorrentContext> createTorrentProcessor() {

        ProcessingStage<TorrentContext> stage3 = new ProcessTorrentStage<>(null, torrentRegistry, trackerService, executor);

        ProcessingStage<TorrentContext> stage2 = new InitializeTorrentProcessingStage<>(stage3, torrentRegistry,
                dataWorkerFactory, config);

        ProcessingStage<TorrentContext> stage1 = new CreateSessionStage<>(stage2, torrentRegistry, peerRegistry,
                connectionPool, messageDispatcher, messagingAgents, config);

        ProcessingStage<TorrentContext> stage0 = new FetchTorrentStage(stage1);

        return stage0;
    }

    private ProcessingStage<MagnetContext> createMagnetProcessor() {

        ProcessingStage<MagnetContext> stage3 = new ProcessMagnetTorrentStage(null, torrentRegistry, trackerService, executor);

        ProcessingStage<MagnetContext> stage2 = new InitializeMagnetTorrentProcessingStage(stage3, torrentRegistry,
                dataWorkerFactory, config);

        ProcessingStage<MagnetContext> stage1 = new FetchMetadataStage(stage2, metadataService, torrentRegistry, trackerService);

        ProcessingStage<MagnetContext> stage0 = new CreateSessionStage<>(stage1, torrentRegistry, peerRegistry,
                connectionPool, messageDispatcher, messagingAgents, config);

        return stage0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ProcessingContext> ProcessingStage<C> processor(Class<C> contextType) {
        return (ProcessingStage<C>) processors.get(contextType);
    }
}
