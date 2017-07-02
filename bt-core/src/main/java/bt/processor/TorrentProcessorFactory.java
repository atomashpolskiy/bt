package bt.processor;

import bt.module.ClientExecutor;
import bt.processor.torrent.FetchTorrentStage;
import bt.processor.torrent.ProcessTorrentStage;
import bt.processor.torrent.RegisterTorrentStage;
import bt.processor.torrent.TorrentContext;
import bt.runtime.Config;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.IDataWorkerFactory;
import bt.tracker.ITrackerService;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class TorrentProcessorFactory implements ProcessorFactory {

    private TorrentRegistry torrentRegistry;
    private IDataWorkerFactory dataWorkerFactory;
    private ITrackerService trackerService;
    private ExecutorService executor;
    private Config config;

    private final Map<Class<?>, ProcessingStage<?>> processors;

    @Inject
    public TorrentProcessorFactory(TorrentRegistry torrentRegistry,
                                   IDataWorkerFactory dataWorkerFactory,
                                   ITrackerService trackerService,
                                   @ClientExecutor ExecutorService executor,
                                   Config config) {
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
        this.trackerService = trackerService;
        this.executor = executor;
        this.config = config;

        this.processors = processors();
    }

    private Map<Class<?>, ProcessingStage<?>> processors() {
        Map<Class<?>, ProcessingStage<?>> processors = new HashMap<>();

        processors.put(TorrentContext.class, createTorrentProcessor());

        return processors;
    }

    private ProcessingStage<TorrentContext> createTorrentProcessor() {

        ProcessingStage<TorrentContext> stage2 = new ProcessTorrentStage(null, torrentRegistry,
                dataWorkerFactory, trackerService, executor, config);

        ProcessingStage<TorrentContext> stage1 = new RegisterTorrentStage(stage2, torrentRegistry);

        ProcessingStage<TorrentContext> stage0 = new FetchTorrentStage(stage1);

        return stage0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ProcessingContext> ProcessingStage<C> processor(Class<C> contextType) {
        return (ProcessingStage<C>) processors.get(contextType);
    }
}
