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
import bt.event.EventSource;
import bt.metainfo.TorrentId;
import bt.net.IConnectionSource;
import bt.net.IMessageDispatcher;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.ProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.DefaultTorrentSessionState;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.DefaultMessageRouter;
import bt.torrent.messaging.IPeerWorkerFactory;
import bt.torrent.messaging.MessageRouter;
import bt.torrent.messaging.PeerWorkerFactory;
import bt.torrent.messaging.TorrentWorker;

import java.util.Set;
import java.util.function.Supplier;

public class CreateSessionStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private TorrentRegistry torrentRegistry;
    private EventSource eventSource;
    private IConnectionSource connectionSource;
    private IMessageDispatcher messageDispatcher;
    private Set<Object> messagingAgents;
    private Config config;

    public CreateSessionStage(ProcessingStage<C> next,
                              TorrentRegistry torrentRegistry,
                              EventSource eventSource,
                              IConnectionSource connectionSource,
                              IMessageDispatcher messageDispatcher,
                              Set<Object> messagingAgents,
                              Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.eventSource = eventSource;
        this.connectionSource = connectionSource;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        TorrentId torrentId = context.getTorrentId().get();
        TorrentDescriptor descriptor = torrentRegistry.register(torrentId);

        MessageRouter router = new DefaultMessageRouter(messagingAgents);
        IPeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(router);

        Supplier<Bitfield> bitfieldSupplier = context::getBitfield;
        Supplier<Assignments> assignmentsSupplier = context::getAssignments;
        Supplier<BitfieldBasedStatistics> statisticsSupplier = context::getPieceStatistics;
        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher, connectionSource, peerWorkerFactory,
                bitfieldSupplier, assignmentsSupplier, statisticsSupplier, eventSource, config);

        context.setState(new DefaultTorrentSessionState(descriptor, torrentWorker));
        context.setRouter(router);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
