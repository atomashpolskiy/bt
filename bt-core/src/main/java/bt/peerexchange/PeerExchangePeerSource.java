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
import bt.peer.PeerSource;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

class PeerExchangePeerSource implements PeerSource {

    private Queue<PeerExchange> messages;
    private volatile Collection<Peer> peers;

    private volatile boolean hasNewPeers;
    private final Object lock;

    PeerExchangePeerSource() {
        messages = new LinkedBlockingQueue<>();
        peers = Collections.emptyList();
        lock = new Object();
    }

    @Override
    public boolean update() {

        if (!hasNewPeers) {
            return false;
        }

        synchronized (lock) {
            peers = collectPeers(messages);
            hasNewPeers = false;
        }
        return true;
    }

    private Collection<Peer> collectPeers(Collection<PeerExchange> messages) {
        Set<Peer> peers = new HashSet<>();
        messages.forEach(message -> {
            message.getAdded().forEach(peers::add);
            message.getDropped().forEach(peers::remove);
        });
        return peers;
    }

    void addMessage(PeerExchange message) {
        synchronized (lock) {
            messages.add(message);
            // according to BEP-11 the same peers can't be dropped in the same message,
            // so it's sufficient to check if list of added peers is not empty
            hasNewPeers = hasNewPeers || !message.getAdded().isEmpty();
        }
    }

    @Override
    public Collection<Peer> getPeers() {
        return peers;
    }
}
