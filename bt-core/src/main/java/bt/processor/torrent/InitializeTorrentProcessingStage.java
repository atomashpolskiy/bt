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

package bt.processor.torrent;

import bt.data.Bitfield;
import bt.event.EventSink;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnectionPool;
import bt.net.extended.ExtendedHandshakeConsumer;
import bt.net.pipeline.IBufferedPieceRegistry;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.DataWorker;
import bt.torrent.messaging.BitfieldConsumer;
import bt.torrent.messaging.GenericConsumer;
import bt.torrent.messaging.MetadataProducer;
import bt.torrent.messaging.PeerRequestConsumer;
import bt.torrent.messaging.PieceConsumer;
import bt.torrent.messaging.RequestProducer;

public class InitializeTorrentProcessingStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private IPeerConnectionPool connectionPool;
    private TorrentRegistry torrentRegistry;
    private DataWorker dataWorker;
    private IBufferedPieceRegistry bufferedPieceRegistry;
    private EventSink eventSink;
    private Config config;

    public InitializeTorrentProcessingStage(ProcessingStage<C> next,
                                            IPeerConnectionPool connectionPool,
                                            TorrentRegistry torrentRegistry,
                                            DataWorker dataWorker,
                                            IBufferedPieceRegistry bufferedPieceRegistry,
                                            EventSink eventSink,
                                            Config config) {
        super(next);
        this.connectionPool = connectionPool;
        this.torrentRegistry = torrentRegistry;
        this.dataWorker = dataWorker;
        this.bufferedPieceRegistry = bufferedPieceRegistry;
        this.eventSink = eventSink;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        Torrent torrent = context.getTorrent().get();
        TorrentDescriptor descriptor = torrentRegistry.register(torrent, context.getStorage());

        TorrentId torrentId = torrent.getTorrentId();
        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        BitfieldBasedStatistics pieceStatistics = createPieceStatistics(bitfield);

        context.getRouter().registerMessagingAgent(GenericConsumer.consumer());
        context.getRouter().registerMessagingAgent(new BitfieldConsumer(bitfield, pieceStatistics, eventSink));
        context.getRouter().registerMessagingAgent(new ExtendedHandshakeConsumer(connectionPool));
        context.getRouter().registerMessagingAgent(new PieceConsumer(torrentId, bitfield, dataWorker, bufferedPieceRegistry, eventSink));
        context.getRouter().registerMessagingAgent(new PeerRequestConsumer(torrentId, dataWorker));
        context.getRouter().registerMessagingAgent(new RequestProducer(descriptor.getDataDescriptor(), config.getMaxOutstandingRequests()));
        context.getRouter().registerMessagingAgent(new MetadataProducer(() -> context.getTorrent().orElse(null), config));

        context.setBitfield(bitfield);
        context.setPieceStatistics(pieceStatistics);
    }

    private BitfieldBasedStatistics createPieceStatistics(Bitfield bitfield) {
        return new BitfieldBasedStatistics(bitfield);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
