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

package bt.it.fixture;

import bt.runtime.BtClient;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
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

    private Collection<Closeable> resources;
    private Collection<SwarmPeer> peers;

    Swarm(Collection<Closeable> resources, Collection<SwarmPeer> peers) {
        this.resources = resources;
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
     * @return List of seeder handles (as of time of calling this method)
     * @since 1.5
     */
    public List<BtClient> getSeederHandles() {
        return peers.stream().filter(SwarmPeer::isSeeding).map(SwarmPeer::getHandle).collect(Collectors.toList());
    }

    /**
     * @return List of leechers (as of time of calling this method)
     * @since 1.0
     */
    public List<SwarmPeer> getLeechers() {
        return peers.stream().filter(peer -> !peer.isSeeding()).collect(Collectors.toList());
    }

    /**
     * @return List of leecher handles (as of time of calling this method)
     * @since 1.5
     */
    public List<BtClient> getLeecherHandles() {
        return peers.stream().filter(peer -> !peer.isSeeding()).map(SwarmPeer::getHandle).collect(Collectors.toList());
    }

    @Override
    protected void after() {
        shutdown();
    }

    public void shutdown() {
        resources.forEach(resource -> {
            try {
                resource.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close swarm resource", e);
            }
        });

        peers.forEach(peer -> {
            try {
                peer.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to shutdown swarm peer: " + peer, e);
            }
        });
    }
}
