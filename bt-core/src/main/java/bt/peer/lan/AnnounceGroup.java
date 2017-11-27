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

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Configuration object, that represents an LSD multicast group.
 *
 * @since 1.6
 */
public class AnnounceGroup {

    private final InetSocketAddress address;
    private final int timeToLive;

    /**
     * @param address IP4 or IP6 multicast address
     * @param timeToLive IP_MULTICAST_TTL socket option.
     *                   Minimal value is 1; increase to allow a broader announce scope, than the local subnet.
     * @since 1.6
     */
    public AnnounceGroup(InetSocketAddress address, int timeToLive) {
        Objects.requireNonNull(address);
        if (!address.getAddress().isMulticastAddress()) {
            throw new IllegalArgumentException("Not a multicast address: " + address.getAddress());
        }
        if (timeToLive < 1) {
            throw new IllegalArgumentException("Illegal TTL: " + timeToLive + ". Must be greater than or equal to 1");
        }
        this.address = address;
        this.timeToLive = timeToLive;
    }

    /**
     * @since 1.6
     */
    public InetSocketAddress getAddress() {
        return address;
    }

    /**
     * @since 1.6
     */
    public int getTimeToLive() {
        return timeToLive;
    }
}
