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

package bt.torrent.messaging;

import bt.event.EventSink;
import bt.net.Peer;
import bt.protocol.BitOrder;
import bt.protocol.Bitfield;
import bt.protocol.Have;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.annotation.Consumes;

/**
 * Consumes peer bitfield.
 *
 * <p>Note that the local bitfield is sent to a remote peer
 * during the connection initialization sequence.
 *
 * @see bt.net.BitfieldConnectionHandler
 * @since 1.0
 */
public class BitfieldConsumer {

    private bt.data.Bitfield bitfield;
    private BitfieldBasedStatistics pieceStatistics;
    private EventSink eventSink;

    public BitfieldConsumer(bt.data.Bitfield bitfield, BitfieldBasedStatistics pieceStatistics, EventSink eventSink) {
        this.bitfield = bitfield;
        this.pieceStatistics = pieceStatistics;
        this.eventSink = eventSink;
    }

    @Consumes
    public void consume(Bitfield bitfieldMessage, MessageContext context) {
        Peer peer = context.getPeer();
        bt.data.Bitfield peerBitfield = new bt.data.Bitfield(bitfieldMessage.getBitfield(), BitOrder.LITTLE_ENDIAN, bitfield.getPiecesTotal());
        pieceStatistics.addBitfield(peer, peerBitfield);
        eventSink.firePeerBitfieldUpdated(context.getTorrentId().get(), peer, peerBitfield);
    }

    @Consumes
    public void consume(Have have, MessageContext context) {
        Peer peer = context.getPeer();
        pieceStatistics.addPiece(peer, have.getPieceIndex());
        pieceStatistics.getPeerBitfield(peer).ifPresent(
                bitfield -> eventSink.firePeerBitfieldUpdated(context.getTorrentId().get(), peer, bitfield));
    }
}
