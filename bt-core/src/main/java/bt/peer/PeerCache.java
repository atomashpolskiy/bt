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

package bt.peer;

import bt.net.InetPeer;
import bt.net.Peer;
import bt.net.PeerId;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class PeerCache implements IPeerCache {
    // all known peers (lookup by inet address)
    private final ConcurrentMap<InetSocketAddress, UpdatablePeer> knownPeers;
    private final ReentrantLock peerLock;

    PeerCache() {
        this.knownPeers = new ConcurrentHashMap<>();
        this.peerLock = new ReentrantLock();
    }

    // need to do this atomically:
    // - concurrent call to getPeerForAddress(InetSocketAddress)
    //   might coincide with querying peer sources (overwriting options, etc)
    @Override
    public Peer store(Peer peer) {
        peerLock.lock();
        try {
            UpdatablePeer newPeer = new UpdatablePeer(peer);
            UpdatablePeer existing = knownPeers.putIfAbsent(peer.getInetSocketAddress(), newPeer);
            if (existing != null) {
                existing.setOptions(peer.getOptions());
            }
            return (existing == null) ? newPeer : existing;
        } finally {
            peerLock.unlock();
        }
    }

    @Override
    public Peer getPeerForAddress(InetSocketAddress address) {
        Peer existing = knownPeers.get(address);
        if (existing == null) {
            peerLock.lock();
            try {
                existing = knownPeers.get(address);
                if (existing == null) {
                    existing = store(new InetPeer(address));
                }
            } finally {
                peerLock.unlock();
            }
        }
        return existing;
    }

    private static class UpdatablePeer implements Peer {
    private final Peer delegate;
    private volatile PeerOptions options;

    UpdatablePeer(Peer delegate) {
            super();
            this.delegate = delegate;
            this.options = delegate.getOptions();
        }

        @Override
        public InetSocketAddress getInetSocketAddress() {
            return delegate.getInetSocketAddress();
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public Optional<PeerId> getPeerId() {
            return delegate.getPeerId();
        }

        @Override
        public PeerOptions getOptions() {
            return options;
        }

        void setOptions(PeerOptions options) {
            this.options = options;
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return delegate.equals(object);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
