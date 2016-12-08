package bt.it.fixture;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Peer swarm.
 *
 * <p>Note that each instance of this class is an {@link ExternalResource}
 * and should be annotated with {@link org.junit.Rule}.
 *
 * @since 1.0
 */
public class Swarm extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(Swarm.class);

    private Collection<SwarmPeer> peers;

    Swarm(Collection<SwarmPeer> peers) {
        this.peers = peers;
    }

    /**
     * @return List of seeders (as of time of calling this method)
     * @since 1.0
     */
    public List<SwarmPeer> getSeeders() {
        return peers.stream().filter(SwarmPeer::isSeeding).collect(Collectors.toList());
    }

    /**
     * @return List of leechers (as of time of calling this method)
     * @since 1.0
     */
    public List<SwarmPeer> getLeechers() {
        return peers.stream().filter(peer -> !peer.isSeeding()).collect(Collectors.toList());
    }

    @Override
    protected void after() {
        shutdown();
    }

    private void shutdown() {
        peers.forEach(peer -> {
            try {
                peer.close();
            } catch (IOException e) {
                LOGGER.warn("Error on swarm shutdown", e);
            }
        });
    }
}
