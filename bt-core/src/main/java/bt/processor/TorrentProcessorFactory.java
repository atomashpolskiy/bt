/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.processor;

import bt.event.EventSink;
import bt.event.EventSource;
import bt.metainfo.IMetadataService;
import bt.module.ClientExecutor;
import bt.module.MessagingAgents;
import bt.net.IConnectionSource;
import bt.net.IMessageDispatcher;
import bt.peer.IPeerRegistry;
import bt.processor.magnet.FetchMetadataStage;
import bt.processor.magnet.InitializeMagnetTorrentProcessingStage;
import bt.processor.magnet.MagnetContext;
import bt.processor.magnet.ProcessMagnetTorrentStage;
import bt.processor.torrent.ChooseFilesStage;
import bt.processor.torrent.CreateSessionStage;
import bt.processor.torrent.FetchTorrentStage;
import bt.processor.torrent.InitializeTorrentProcessingStage;
import bt.processor.torrent.ProcessTorrentStage;
import bt.processor.torrent.SeedStage;
import bt.processor.torrent.TorrentContext;
import bt.processor.torrent.TorrentContextFinalizer;
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
    private IConnectionSource connectionSource;
    private IMessageDispatcher messageDispatcher;
    private Set<Object> messagingAgents;
    private IMetadataService metadataService;
    private EventSource eventSource;
    private EventSink eventSink;
    private Config config;

    private final Map<Class<?>, Processor<?>> processors;

    @Inject
    public TorrentProcessorFactory(TorrentRegistry torrentRegistry,
                                   IDataWorkerFactory dataWorkerFactory,
                                   ITrackerService trackerService,
                                   @ClientExecutor ExecutorService executor,
                                   IPeerRegistry peerRegistry,
                                   IConnectionSource connectionSource,
                                   IMessageDispatcher messageDispatcher,
                                   @MessagingAgents Set<Object> messagingAgents,
                                   IMetadataService metadataService,
                                   EventSource eventSource,
                                   EventSink eventSink,
                                   Config config) {
        this.torrentRegistry = torrentRegistry;
        this.dataWorkerFactory = dataWorkerFactory;
        this.trackerService = trackerService;
        this.executor = executor;
        this.peerRegistry = peerRegistry;
        this.connectionSource = connectionSource;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
        this.metadataService = metadataService;
        this.eventSource = eventSource;
        this.eventSink = eventSink;
        this.config = config;

        this.processors = processors();
    }

    private Map<Class<?>, Processor<?>> processors() {
        Map<Class<?>, Processor<?>> processors = new HashMap<>();

        processors.put(TorrentContext.class, createTorrentProcessor());
        processors.put(MagnetContext.class, createMagnetProcessor());

        return processors;
    }

    protected ChainProcessor<TorrentContext> createTorrentProcessor() {

        ProcessingStage<TorrentContext> stage5 = new SeedStage<>(null, torrentRegistry);

        ProcessingStage<TorrentContext> stage4 = new ProcessTorrentStage<>(stage5, torrentRegistry, trackerService);

        ProcessingStage<TorrentContext> stage3 = new ChooseFilesStage<>(stage4, torrentRegistry, config);

        ProcessingStage<TorrentContext> stage2 = new InitializeTorrentProcessingStage<>(stage3, torrentRegistry,
                dataWorkerFactory, eventSink, config);

        ProcessingStage<TorrentContext> stage1 = new CreateSessionStage<>(stage2, torrentRegistry, eventSource,
                connectionSource, messageDispatcher, messagingAgents, config);

        ProcessingStage<TorrentContext> stage0 = new FetchTorrentStage(stage1);

        return new ChainProcessor<>(stage0, executor, new TorrentContextFinalizer<>(torrentRegistry));
    }

    protected ChainProcessor<MagnetContext> createMagnetProcessor() {

        ProcessingStage<MagnetContext> stage5 = new SeedStage<>(null, torrentRegistry);

        ProcessingStage<MagnetContext> stage4 = new ProcessMagnetTorrentStage(stage5, torrentRegistry, trackerService);

        ProcessingStage<MagnetContext> stage3 = new ChooseFilesStage<>(stage4, torrentRegistry, config);

        ProcessingStage<MagnetContext> stage2 = new InitializeMagnetTorrentProcessingStage(stage3, torrentRegistry,
                dataWorkerFactory, eventSink, config);

        ProcessingStage<MagnetContext> stage1 = new FetchMetadataStage(stage2, metadataService, torrentRegistry,
                trackerService, peerRegistry, config);

        ProcessingStage<MagnetContext> stage0 = new CreateSessionStage<>(stage1, torrentRegistry, eventSource,
                connectionSource, messageDispatcher, messagingAgents, config);

        return new ChainProcessor<>(stage0, executor, new TorrentContextFinalizer<>(torrentRegistry));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ProcessingContext> Processor<C> processor(Class<C> contextType) {
        return (Processor<C>) processors.get(contextType);
    }
}
