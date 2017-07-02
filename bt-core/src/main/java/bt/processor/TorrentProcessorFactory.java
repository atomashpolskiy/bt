package bt.processor;

import bt.processor.torrent.FetchTorrentStage;
import bt.processor.torrent.ProcessTorrentStage;
import bt.processor.torrent.RegisterTorrentStage;
import bt.processor.torrent.TorrentContext;
import bt.runtime.Config;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.IDataWorkerFactory;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;

public class TorrentProcessorFactory implements ProcessorFactory {

    private TorrentRegistry torrentRegistry;
    private IDataWorkerFactory dataWorkerFactory;
    private Config config;

    private final Map<Class<?>, ProcessingStage<?>> processors;

    @Inject
    public TorrentProcessorFactory(TorrentRegistry torrentRegistry,
                                   IDataWorkerFactory dataWorkerFactory,
                                   Config config) {
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
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
                dataWorkerFactory, config);

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
