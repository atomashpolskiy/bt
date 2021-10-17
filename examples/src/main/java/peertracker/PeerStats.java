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
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.net.InetAddress;
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

    private final Map<CounterKey, Counter> counters = new ConcurrentHashMap<>(5000);

    public void onPeerDiscovered(PeerDiscoveredEvent event) {
        final CounterKey counterKey = new CounterKey(event.getPeer().getInetAddress(), event.getPeer().getPort());
        getCounter(counterKey).incrementDiscovered();
    }

    public boolean onPeerConnected(PeerConnectedEvent event) {
        final CounterKey counterKey = new CounterKey(event.getPeer().getInetAddress(), event.getRemotePort());
        getCounter(counterKey).incrementConnected();
        return true;
    }

    public void onPeerDisconnected(PeerDisconnectedEvent event) {
        final CounterKey counterKey = new CounterKey(event.getPeer().getInetAddress(), event.getRemotePort());
        getCounter(counterKey).incrementDisconnected();
    }

    public void onPeerBitfieldUpdated(PeerBitfieldUpdatedEvent event) {
        final CounterKey counterKey = new CounterKey(event.getPeer().getInetAddress(),
                event.getConnectionKey().getRemotePort());
        Counter counter = getCounter(counterKey);
        counter.setPiecesCompleted(event.getBitfield().getPiecesComplete());
        counter.setPiecesRemaining(event.getBitfield().getPiecesIncomplete());
    }

    private Counter getCounter(CounterKey counterKey) {
        Counter counter = counters.computeIfAbsent(counterKey, ck -> new Counter());
        return counter;
    }

    public Map<CounterKey, Counter> getCounters() {
        return counters;
    }

    /**
     * To uniquely identify a connection we use the remote address, and remote port. This uniquely identifies a
     * connection for a unique torrent. The remote port will always stay constant for a connection, and the remote port
     * will always match the connecting port on an outgoing connection, ensuring that the peer discovery events are
     * also properly matched.
     */
    public static class CounterKey {
        private final InetAddress addr;
        private final int remotePort;

        public CounterKey(InetAddress addr, int remotePort) {
            this.addr = addr;
            this.remotePort = remotePort;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CounterKey that = (CounterKey) o;
            return remotePort == that.remotePort && Objects.equal(addr, that.addr);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(addr, remotePort);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("addr", addr)
                    .add("remotePort", remotePort)
                    .toString();
        }
    }
}
