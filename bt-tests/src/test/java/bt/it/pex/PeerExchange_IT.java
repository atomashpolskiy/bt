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

package bt.it.pex;

import bt.it.fixture.BaseBtTest;
import bt.it.fixture.SharedTrackerModule;
import bt.it.fixture.SharedTrackerModule.PeerFilter;
import bt.it.fixture.Swarm;
import bt.it.fixture.SwarmPeer;
import bt.net.Peer;
import bt.peerexchange.PeerExchangeConfig;
import bt.peerexchange.PeerExchangeModule;
import bt.runtime.Config;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class PeerExchange_IT extends BaseBtTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeerExchange_IT.class);

    private static final int PEER_COUNT = 6;

    @Rule
    public Swarm swarm = buildSwarm()
            .seeders(PEER_COUNT)
            .config(new Config() {
                @Override
                public InetAddress getAcceptorAddress() {
                    return InetAddress.getLoopbackAddress();
                }
                @Override
                public Duration getPeerDiscoveryInterval() {
                    return Duration.ofSeconds(1);
                }
                @Override
                public Duration getTrackerQueryInterval() {
                    return Duration.ofMillis(100);
                }
            })
            .module(new PeerExchangeModule(new PeerExchangeConfig() {
                @Override
                public Duration getMinMessageInterval() {
                    return Duration.ofMillis(100);
                }
                @Override
                public int getMinEventsPerMessage() {
                    return 1;
                }
            }))
            .module(new SharedTrackerModule(new PEXPeerFilter(PEER_COUNT, 1)))
            .module(new PeerExchangeModule())
            .useInMemoryFileSystem()
            .build();

    // TODO: fails in Travis when JaCoCo is enabled -- need to investigate
//    @Test
    public void testPeerExchange() {

        ConcurrentMap<Peer, Set<Peer>> discoveredPeers = new ConcurrentHashMap<>();

        swarm.getSeeders().forEach(seeder ->
                seeder.getHandle().startAsync(state -> {
                    Set<Peer> peerPeers = discoveredPeers.get(seeder.getPeer());
                    if (peerPeers == null) {
                        peerPeers = ConcurrentHashMap.newKeySet();
                        Set<Peer> existing = discoveredPeers.putIfAbsent(seeder.getPeer(), peerPeers);
                        if (existing != null) {
                            peerPeers = existing;
                        }
                    }
                    peerPeers.addAll(state.getConnectedPeers());
                }, 1000));

        try {
            Thread.sleep(20000L);
        } catch (InterruptedException e) {
            throw new RuntimeException("Test unexpectedly interrupted", e);
        }

        Set<Peer> swarmPeers = new HashSet<>();
        swarmPeers.addAll(swarm.getSeeders().stream().map(SwarmPeer::getPeer).collect(Collectors.toSet()));

        LOGGER.info("Swarm peers:");
        swarmPeers.forEach(peer -> {
            LOGGER.info("{} ({})", peer, peer.getClass());
        });

        assertTrue(discoveredPeers.keySet().containsAll(swarmPeers));
        discoveredPeers.forEach((peer, peers) -> {
            LOGGER.info("Peer {} discovered the following peers:", peer);
            peers.forEach(discoveredPeer -> {
                LOGGER.info("{} ({})", discoveredPeer, discoveredPeer.getClass());
            });
            for (Peer swarmPeer : swarmPeers) {
                assertContainsPeer(peers, swarmPeer);
            }
        });
    }

    private static void assertContainsPeer(Collection<Peer> peers, Peer peer) {
        assertTrue(peers.contains(peer));
    }

    /**
     * This filter provides information about only one remote peer for each swarm participant,
     * while making sure that there are no disconnected sub-swarms
     */
    private static class PEXPeerFilter implements PeerFilter {

        private int totalNumOfPeers;
        private int numOfPeersToReturn;

        public PEXPeerFilter(int totalNumOfPeers, int numOfPeersToReturn) {
            if (totalNumOfPeers < 0 || numOfPeersToReturn < 0 || (numOfPeersToReturn > totalNumOfPeers)) {
                throw new IllegalArgumentException("Illegal arguments " +
                        "(totalNumOfPeers: " + totalNumOfPeers + ", numOfPeersToReturn: " + numOfPeersToReturn + ")");
            }
            this.totalNumOfPeers = totalNumOfPeers;
            this.numOfPeersToReturn = numOfPeersToReturn;
        }

        @Override
        public Collection<Peer> filterPeers(Peer self, Set<Peer> peers) {

            if (peers.size() < totalNumOfPeers) {
                return Collections.emptyList();
            }

            List<Peer> sortedPeers = new ArrayList<>(peers);
            Collections.sort(sortedPeers, (p1, p2) -> {

                String addr1 = p1.getInetAddress().getHostAddress();
                String addr2 = p2.getInetAddress().getHostAddress();
                int c = addr1.compareTo(addr2);
                return (c == 0) ? Integer.valueOf(p1.getPort()).compareTo(p2.getPort()) : c;
            });

            int currentIndex = sortedPeers.indexOf(self);
            sortedPeers.remove(currentIndex);

            List<Peer> result = new ArrayList<>();
            while (result.size() < numOfPeersToReturn) {
                currentIndex = (currentIndex == 0) ? sortedPeers.size() - 1 : currentIndex - 1;
                result.add(sortedPeers.get(currentIndex));
            }
            return result;
        }
    }
}
