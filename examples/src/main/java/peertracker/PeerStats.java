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

package peertracker;

import bt.event.PeerBitfieldUpdatedEvent;
import bt.event.PeerConnectedEvent;
import bt.event.PeerDisconnectedEvent;
import bt.event.PeerDiscoveredEvent;
import bt.net.Peer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PeerStats {

    public static class Counter {
        private final AtomicLong discoveredTimes = new AtomicLong();
        private final AtomicLong connectedTimes = new AtomicLong();
        private final AtomicLong disconnectedTimes = new AtomicLong();
        private final AtomicInteger piecesCompleted = new AtomicInteger();
        private final AtomicInteger piecesRemaining = new AtomicInteger();

        public void incrementDiscovered() {
            discoveredTimes.addAndGet(1);
        }

        public void incrementConnected() {
            connectedTimes.addAndGet(1);
        }

        public void incrementDisconnected() {
            disconnectedTimes.addAndGet(1);
        }

        public void setPiecesCompleted(int piecesCompleted) {
            this.piecesCompleted.set(piecesCompleted);
        }

        public void setPiecesRemaining(int piecesRemaining) {
            this.piecesRemaining.set(piecesRemaining);
        }

        public long getDiscoveredTimes() {
            return discoveredTimes.get();
        }

        public long getConnectedTimes() {
            return connectedTimes.get();
        }

        public long getDisconnectedTimes() {
            return disconnectedTimes.get();
        }

        public int getPiecesCompleted() {
            return piecesCompleted.get();
        }

        public int getPiecesRemaining() {
            return piecesRemaining.get();
        }
    }

    private final Map<Peer, Counter> counters = new ConcurrentHashMap<>(5000);

    public void onPeerDiscovered(PeerDiscoveredEvent event) {
        getCounter(event.getPeer()).incrementDiscovered();
    }

    public void onPeerConnected(PeerConnectedEvent event) {
        getCounter(event.getPeer()).incrementConnected();
    }

    public void onPeerDisconnected(PeerDisconnectedEvent event) {
        getCounter(event.getPeer()).incrementDisconnected();
    }

    public void onPeerBitfieldUpdated(PeerBitfieldUpdatedEvent event) {
        Counter counter = getCounter(event.getPeer());
        counter.setPiecesCompleted(event.getBitfield().getPiecesComplete());
        counter.setPiecesRemaining(event.getBitfield().getPiecesRemaining());
    }

    private Counter getCounter(Peer peer) {
        Counter counter = counters.get(peer);
        if (counter == null) {
            counter = new Counter();
            Counter existing = counters.putIfAbsent(peer, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        return counter;
    }

    public Map<Peer, Counter> getCounters() {
        return counters;
    }
}
