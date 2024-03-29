/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.event.EventSink;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.peer.IPeerRegistry;
import bt.processor.ProcessingStage;
import bt.processor.TerminateOnErrorProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.torrent.TorrentDescriptor;
import bt.torrent.TorrentRegistry;
import bt.torrent.TrackerAnnouncer;
import bt.tracker.AnnounceKey;
import bt.tracker.ITrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ProcessTorrentStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTorrentStage.class);
    private TorrentRegistry torrentRegistry;
    private final IPeerRegistry peerRegistry;
    private ITrackerService trackerService;
    private EventSink eventSink;

    public ProcessTorrentStage(ProcessingStage<C> next,
                               TorrentRegistry torrentRegistry,
                               IPeerRegistry peerRegistry,
                               ITrackerService trackerService,
                               EventSink eventSink) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.trackerService = trackerService;
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(C context) throws InterruptedException {
        TorrentId torrentId = context.getTorrentId().get();
        TorrentDescriptor descriptor = getDescriptor(torrentId);

        Torrent torrent = context.getTorrent().get();
        Optional<AnnounceKey> announceKey = torrent.getAnnounceKey();
        if (announceKey.isPresent()) {
            TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, torrent, announceKey.get(), context.getState().get());
            context.setAnnouncer(announcer);
        }

        descriptor.start();

        eventSink.fireTorrentStarted(torrentId);

        peerRegistry.triggerPeerCollection(torrentId);
        descriptor.getDataDescriptor().waitForAllPieces();
        complete(context);
    }

    private void complete(C context) {
        try {
            context.getTorrentId().ifPresent(torrentId -> getDescriptor(torrentId).complete());
            onCompleted(context);
        } catch (Exception e) {
            LOGGER.error("Unexpected error", e);
        }
    }

    protected void onCompleted(C context) {
        context.getAnnouncer().ifPresent(TrackerAnnouncer::complete);
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.DOWNLOAD_COMPLETE;
    }
}
