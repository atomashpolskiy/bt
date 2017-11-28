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

import bt.net.InternetProtocolUtils;
import bt.net.SocketChannelConnectionAcceptor;

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static bt.net.InternetProtocolUtils.isIP4;
import static bt.net.InternetProtocolUtils.isIP6;

/**
 * Utility class, that provides necessary info for LSD service, based on application configuration.
 */
class LocalServiceDiscoveryInfo {

    private final Set<Integer> localPorts;
    private final Collection<AnnounceGroup> compatibleGroups;

    public LocalServiceDiscoveryInfo(
            Set<SocketChannelConnectionAcceptor> socketAcceptors,
            Collection<AnnounceGroup> announceGroups) {

        this.localPorts = collectLocalPorts(socketAcceptors);

        boolean acceptIP4 = false;
        boolean acceptIP6 = false;
        for (SocketChannelConnectionAcceptor acceptor : socketAcceptors) {
            InetSocketAddress address = acceptor.getLocalAddress();
            ProtocolFamily protocolFamily = InternetProtocolUtils.getProtocolFamily(address.getAddress());
            if (protocolFamily == StandardProtocolFamily.INET) {
                acceptIP4 = true;
            } else {
                acceptIP6 = true;
            }
            if (acceptIP4 && acceptIP6) {
                break; // no need to look further
            }
        }

        this.compatibleGroups = collectCompatibleGroups(announceGroups, acceptIP4, acceptIP6);
    }

    private Set<Integer> collectLocalPorts(Set<SocketChannelConnectionAcceptor> socketAcceptors) {
        return socketAcceptors.stream().map(a -> a.getLocalAddress().getPort()).collect(Collectors.toSet());
    }

    private Collection<AnnounceGroup> collectCompatibleGroups(
            Collection<AnnounceGroup> groups,
            boolean acceptIP4,
            boolean acceptIP6) {

        return groups.stream()
                .filter(group -> (isIP4(group.getAddress()) && acceptIP4) || (isIP6(group.getAddress()) && acceptIP6))
                .collect(Collectors.toList());
    }

    public Set<Integer> getLocalPorts() {
        return localPorts;
    }

    public Collection<AnnounceGroup> getCompatibleGroups() {
        return compatibleGroups;
    }
}
