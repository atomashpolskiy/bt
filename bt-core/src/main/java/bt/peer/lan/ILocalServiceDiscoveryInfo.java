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

import java.net.NetworkInterface;
import java.util.Collection;
import java.util.Set;

/**
 * Provides information required for operation of Local Service Discovery.
 *
 * @since 1.6
 */
public interface ILocalServiceDiscoveryInfo {

    /**
     * @return Collection of local ports, that the current runtime is listening on
     * @since 1.6
     */
    Set<Integer> getLocalPorts();

    /**
     * @return Collection of groups, that are compatible with the current runtime's network configuration
     * @since 1.6
     */
    Collection<AnnounceGroup> getCompatibleGroups();

    /**
     * @return Collection of network interfaces, that can be used to listen to incoming messages
     * @since 1.6
     */
    Collection<NetworkInterface> getNetworkInterfaces();
}
