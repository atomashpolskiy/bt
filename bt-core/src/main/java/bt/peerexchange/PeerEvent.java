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

package bt.peerexchange;

import bt.net.Peer;
import bt.peer.PeerOptions;

import java.util.function.Supplier;

class PeerEvent implements Comparable<PeerEvent> {

    enum Type { ADDED, DROPPED }

    static PeerEvent added(Peer peer, Supplier<PeerOptions> peerOptionsSupplier) {
        return new PeerEvent(Type.ADDED, peer, peerOptionsSupplier);
    }

    static PeerEvent dropped(Peer peer) {
        return new PeerEvent(Type.DROPPED, peer, null);
    }

    private final Type type;
    private final Peer peer;
    private final Supplier<PeerOptions> peerOptionsSupplier;
    private final long instant;

    /**
     * Construct a new PeerEvent
     *
     * @param type                the type of event
     * @param peer                the peer of the event
     * @param peerOptionsSupplier A supplier to get the most up to date peer options. The options may change from the
     *                            time we add this message and we want to send the most up to date options.
     */
    private PeerEvent(Type type, Peer peer, Supplier<PeerOptions> peerOptionsSupplier) {

        this.type = type;
        this.peer = peer;
        this.peerOptionsSupplier = peerOptionsSupplier;

        instant = System.currentTimeMillis();
    }

    Type getType() {
        return type;
    }

    Peer getPeer() {
        return peer;
    }

    PeerOptions getPeerOptions() {
        return peerOptionsSupplier.get();
    }

    long getInstant() {
        return instant;
    }

    @Override
    public int compareTo(PeerEvent o) {

        if (instant == o.getInstant()) {
            return 0;
        } else if (instant - o.getInstant() >= 0) {
            return 1;
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "PeerEvent {type=" + type + ", peer=" + peer + ", instant=" + instant + '}';
    }
}
