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

package bt.net;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Holds parts of inet address and resolves it on demand.
 * Helps prevent unsolicited blocking that can happen when directly creating an {@link InetSocketAddress}.
 *
 * @since 1.3
 */
public class InetPeerAddress {

    private final String hostname;
    private final int port;
    private final int hashCode;

    private volatile InetSocketAddress address;
    private final Object lock;

    /**
     * @since 1.3
     */
    public InetPeerAddress(String hostname, int port) {
        this.hostname = Objects.requireNonNull(hostname);
        this.port = port;
        this.hashCode = 31 * hostname.hashCode() + port;
        this.lock = new Object();
    }

    /**
     * @since 1.3
     */
    public InetSocketAddress getAddress() {
        if (address == null) {
            synchronized (lock) {
                if (address == null) {
                    address = new InetSocketAddress(hostname, port);
                }
            }
        }
        return address;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || !getClass().equals(object.getClass())) {
            return false;
        }

        InetPeerAddress that = (InetPeerAddress) object;
        return port == that.port && hostname.equals(that.hostname);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
