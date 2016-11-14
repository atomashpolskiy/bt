package bt.it.pex;

import bt.it.fixture.BaseBtTest;
import bt.it.fixture.PersonalizedThreadNamesFeature;
import bt.it.fixture.SharedTrackerFeature;
import bt.it.fixture.SharedTrackerFeature.PeerFilter;
import bt.it.fixture.Swarm;
import bt.it.fixture.SwarmPeer;
import bt.it.fixture.TestConfigurationFeature;
import bt.peerexchange.PeerExchangeModule;
import bt.net.InetPeer;
import bt.net.Peer;
import org.junit.Rule;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static final int SEEDERS_COUNT = 3;
    private static final int LEECHERS_COUNT = 3;

    @Rule
    public Swarm swarm = buildSwarm().withoutFiles()
            .seeders(SEEDERS_COUNT).leechers(LEECHERS_COUNT).startingPort(6891).build();

    public PeerExchange_IT() {
        super(Arrays.asList(
                (configuration, runtimeBuilder) -> runtimeBuilder.module(new PeerExchangeModule()),
                new PersonalizedThreadNamesFeature(), new TestConfigurationFeature(),
                new SharedTrackerFeature(new PEXPeerFilter(SEEDERS_COUNT + LEECHERS_COUNT, 1))));
    }

    // disabling the test until proper configuration management is added to Bt
//    @Test
    public void testPeerExchange() {

        ConcurrentMap<Peer, Set<Peer>> discoveredPeers = new ConcurrentHashMap<>();

        swarm.getLeechers().forEach(leecher ->
                leecher.getHandle().startAsync(state -> discoveredPeers.put(leecher.getPeer(), state.getConnectedPeers()), 1000));

        swarm.getSeeders().forEach(seeder ->
                seeder.getHandle().startAsync(state -> discoveredPeers.put(seeder.getPeer(), state.getConnectedPeers()), 1000));

        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            throw new RuntimeException("Test unexpectedly interrupted", e);
        }

        Set<Peer> swarmPeers = new HashSet<>();
        swarmPeers.addAll(swarm.getLeechers().stream().map(SwarmPeer::getPeer).collect(Collectors.toSet()));
        swarmPeers.addAll(swarm.getSeeders().stream().map(SwarmPeer::getPeer).collect(Collectors.toSet()));

        assertTrue(discoveredPeers.keySet().containsAll(swarmPeers));
        for (Set<Peer> peerPeers : discoveredPeers.values()) {
            for (Peer swarmPeer : swarmPeers) {
                assertContainsPeerWithOrWithoutId(peerPeers, swarmPeer);
            }
        }
    }

    private static void assertContainsPeerWithOrWithoutId(Collection<Peer> peers, Peer peer) {
        if (peer.getPeerId().isPresent()) {
            Peer peerWithoutId = new InetPeer(peer.getInetAddress(), peer.getPort());
            assertTrue(peers.contains(peer) || peers.contains(peerWithoutId));
        } else {
            assertTrue(peers.contains(peer));
        }
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
