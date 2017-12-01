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

package bt.peer.lan;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

/**
 * @since 1.6
 */
public class LocalServiceDiscoveryConfig {

    private Duration localServiceDiscoveryAnnounceInterval;
    private int localServiceDiscoveryMaxTorrentsPerAnnounce;
    private Collection<AnnounceGroup> localServiceDiscoveryAnnounceGroups;

    /**
     * @since 1.6
     */
    public LocalServiceDiscoveryConfig() {
        this.localServiceDiscoveryAnnounceInterval = Duration.ofSeconds(60);
        this.localServiceDiscoveryMaxTorrentsPerAnnounce = 5;

        try {
            InetAddress ip4multicast = InetAddress.getByName("239.192.152.143");
            InetAddress ip6multicast = InetAddress.getByName("[ff15::efc0:988f]");
            this.localServiceDiscoveryAnnounceGroups = Arrays.asList(
                    new AnnounceGroup(new InetSocketAddress(ip4multicast, 6771), 1),
                    new AnnounceGroup(new InetSocketAddress(ip6multicast, 6771), 1));
        } catch (UnknownHostException e) {
            // can't happen
        }
    }

    /**
     * @param localServiceDiscoveryAnnounceInterval Interval of LSD announce
     * @since 1.6
     */
    public void setLocalServiceDiscoveryAnnounceInterval(Duration localServiceDiscoveryAnnounceInterval) {
        this.localServiceDiscoveryAnnounceInterval = localServiceDiscoveryAnnounceInterval;
    }

    /**
     * @since 1.6
     */
    public Duration getLocalServiceDiscoveryAnnounceInterval() {
        return localServiceDiscoveryAnnounceInterval;
    }

    /**
     * @param localServiceDiscoveryMaxTorrentsPerAnnounce Max number of infohashes to include into an LSD announce.
     *                                                    BEP-14 recommends no more than 5 due to possible UDP datagram fragmentation
     * @since 1.6
     */
    public void setLocalServiceDiscoveryMaxTorrentsPerAnnounce(int localServiceDiscoveryMaxTorrentsPerAnnounce) {
        this.localServiceDiscoveryMaxTorrentsPerAnnounce = localServiceDiscoveryMaxTorrentsPerAnnounce;
    }

    /**
     * @since 1.6
     */
    public int getLocalServiceDiscoveryMaxTorrentsPerAnnounce() {
        return localServiceDiscoveryMaxTorrentsPerAnnounce;
    }

    /**
     * @param localServiceDiscoveryAnnounceGroups ASM multicast groups for Local Service Discovery.
     *                                            BEP-14 defaults are 239.192.152.143:6771 and [ff15::efc0:988f]:6771
     * @since 1.6
     */
    public void setLocalServiceDiscoveryAnnounceGroups(Collection<AnnounceGroup> localServiceDiscoveryAnnounceGroups) {
        this.localServiceDiscoveryAnnounceGroups = localServiceDiscoveryAnnounceGroups;
    }

    /**
     * @since 1.6
     */
    public Collection<AnnounceGroup> getLocalServiceDiscoveryAnnounceGroups() {
        return localServiceDiscoveryAnnounceGroups;
    }
}
