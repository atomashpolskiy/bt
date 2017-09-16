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

package bt.dht;

import bt.net.InetPeerAddress;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Provides configuration for DHT facilities.
 *
 * @since 1.1
 */
public class DHTConfig {

    private int listeningPort;
    private boolean useRouterBootstrap;
    private boolean useIPv6;
    private Collection<InetPeerAddress> bootstrapNodes;

    private final Collection<InetPeerAddress> publicBootstrapNodes;

    /**
     * @since 1.1
     */
    public DHTConfig() {
        this.listeningPort = 49001;
        this.useRouterBootstrap = false;
        this.useIPv6 = false;
        this.bootstrapNodes = Collections.emptyList();

        this.publicBootstrapNodes = Arrays.asList(
            new InetPeerAddress("router.bittorrent.com", 6881),
            new InetPeerAddress("dht.transmissionbt.com", 6881),
            new InetPeerAddress("router.utorrent.com", 6881)
        );
    }

    /**
     * @see #setListeningPort(int)
     * @since 1.1
     */
    public int getListeningPort() {
        return listeningPort;
    }

    /**
     * @param listeningPort Local port the DHT service will be listening on
     * @since 1.1
     */
    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }

    /**
     * @see #setShouldUseRouterBootstrap(boolean)
     * @since 1.1
     */
    public boolean shouldUseRouterBootstrap() {
        return useRouterBootstrap;
    }

    /**
     * @param useRouterBootstrap Indicates whether public bootstrap services will be used
     * @since 1.1
     */
    public void setShouldUseRouterBootstrap(boolean useRouterBootstrap) {
        this.useRouterBootstrap = useRouterBootstrap;
    }

    /**
     * @see #shouldUseIPv6()
     * @since 1.1
     */
    public boolean shouldUseIPv6() {
        return useIPv6;
    }

    /**
     * @param useIPv6 Indicates whether IPv6 interface should be preferred for attaching the local DHT node.
     *                Should not be set to true, if no IPv6 interfaces are available on current system.
     * @since 1.1
     */
    public void setShouldUseIPv6(boolean useIPv6) {
        this.useIPv6 = useIPv6;
    }

    /**
     * @see #setBootstrapNodes(Collection)
     * @since 1.3
     */
    public Collection<InetPeerAddress> getBootstrapNodes() {
        return bootstrapNodes;
    }

    /**
     * @param bootstrapNodes DHT nodes to use upon startup to connect to the swarm.
     * @since 1.3
     */
    public void setBootstrapNodes(Collection<InetPeerAddress> bootstrapNodes) {
        this.bootstrapNodes = bootstrapNodes;
    }

    /***************** NOT PUBLIC ******************/

    /**
     * @return Collection of public bootstrap nodes (routers)
     * @since 1.3
     */
    Collection<InetPeerAddress> getPublicBootstrapNodes() {
        return publicBootstrapNodes;
    }
}
