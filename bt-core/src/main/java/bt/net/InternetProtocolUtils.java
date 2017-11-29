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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;

/**
 * @since 1.6
 */
public class InternetProtocolUtils {
    private static final int IP4_BYTES = 4;
    private static final int IP6_BYTES = 16;

    /**
     * @return {@link StandardProtocolFamily#INET} for IPv4 address or {@link StandardProtocolFamily#INET6} for IPv6 address
     * @throws IllegalArgumentException if the address is neither IPv4 or IPv6
     * @since 1.6
     */
    public static ProtocolFamily getProtocolFamily(InetAddress address) {
        if (address.getAddress().length == IP4_BYTES) {
            return StandardProtocolFamily.INET;
        } else if (address.getAddress().length == IP6_BYTES) {
            return StandardProtocolFamily.INET6;
        } else {
            throw new IllegalArgumentException("Can't determine protocol family for address: " + address);
        }
    }

    /**
     * @since 1.6
     */
    public static boolean isIP4(InetSocketAddress address) {
        return getProtocolFamily(address.getAddress()) == StandardProtocolFamily.INET;
    }

    /**
     * @since 1.6
     */
    public static boolean isIP6(InetSocketAddress address) {
        return getProtocolFamily(address.getAddress()) == StandardProtocolFamily.INET6;
    }

    /**
     * Returns {@link InetAddress#toString()} without the hostname part and forward slash.
     *
     * @since 1.6
     */
    public static String getLiteralIP(InetAddress address) {
        String s = address.toString();
        int k = s.indexOf('/');
        if (k >= 0) {
            // slash is never the last character
            s = s.substring(k + 1);
        }
        return s;
    }
}
