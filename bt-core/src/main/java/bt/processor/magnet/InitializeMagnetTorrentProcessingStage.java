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

package bt.processor.magnet;

import bt.data.Bitfield;
import bt.event.EventSink;
import bt.metainfo.TorrentId;
import bt.net.Peer;
import bt.processor.ProcessingStage;
import bt.processor.listener.ProcessingEvent;
import bt.processor.torrent.InitializeTorrentProcessingStage;
import bt.protocol.BitOrder;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentRegistry;
import bt.torrent.data.IDataWorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;

public class InitializeMagnetTorrentProcessingStage extends InitializeTorrentProcessingStage<MagnetContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InitializeMagnetTorrentProcessingStage.class);

    private EventSink eventSink;

    public InitializeMagnetTorrentProcessingStage(ProcessingStage<MagnetContext> next,
                                                  TorrentRegistry torrentRegistry,
                                                  IDataWorkerFactory dataWorkerFactory,
                                                  EventSink eventSink,
                                                  Config config) {
        super(next, torrentRegistry, dataWorkerFactory, eventSink, config);
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        super.doExecute(context);

        TorrentId torrentId = context.getTorrentId().get();

        BitfieldBasedStatistics statistics = context.getPieceStatistics();
        // process bitfields and haves that we received while fetching metadata
        Collection<Peer> peersUpdated = new HashSet<>();
        context.getBitfieldConsumer().getBitfields().forEach((peer, bitfieldBytes) -> {
            if (statistics.getPeerBitfield(peer).isPresent()) {
                // we should not have received peer's bitfields twice, but whatever.. ignore and continue
                return;
            }
            try {
                peersUpdated.add(peer);
                statistics.addBitfield(peer, new Bitfield(bitfieldBytes, BitOrder.LITTLE_ENDIAN, statistics.getPiecesTotal()));
            } catch (Exception e) {
                LOGGER.warn("Error happened when processing peer's bitfield", e);
            }
        });
        context.getBitfieldConsumer().getHaves().forEach((peer, pieces) -> {
            try {
                peersUpdated.add(peer);
                pieces.forEach(piece -> statistics.addPiece(peer, piece));
            } catch (Exception e) {
                LOGGER.warn("Error happened when processing peer's haves", e);
            }
        });
        peersUpdated.forEach(peer -> {
            // racing against possible disconnection of peers, so must check if bitfield is still present
            statistics.getPeerBitfield(peer).ifPresent(
                    bitfield -> eventSink.firePeerBitfieldUpdated(torrentId, peer, bitfield));
        });
        // unregistering only now, so that there were no gaps in bitifield receiving
        context.getRouter().unregisterMessagingAgent(context.getBitfieldConsumer());
        context.setBitfieldConsumer(null); // mark for gc collection
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
