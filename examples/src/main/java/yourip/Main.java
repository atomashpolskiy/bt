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

package yourip;

import bt.Bt;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.runtime.BtClient;
import bt.runtime.Config;
import yourip.mock.MockModule;
import yourip.mock.MockStorage;
import yourip.mock.MockTorrent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Main {

    private static final int[] ports = new int[] {6891, 6892};
    private static final Set<Peer> peers = new HashSet<Peer>() {{
        for (int port : ports) {
            add(new InetPeer(InetAddress.getLoopbackAddress(), port));
        }
    }};

    public static Set<Peer> peers() {
        return Collections.unmodifiableSet(peers);
    }

    public static void main(String[] args) throws InterruptedException {
        Collection<BtClient> clients = new HashSet<>();
        for (int port : ports) {
            clients.add(buildClient(port));
        }

        clients.forEach(BtClient::startAsync);

        Thread.sleep(10000);

        clients.forEach(BtClient::stop);
    }

    private static BtClient buildClient(int port) {
        Config config = new Config() {
            @Override
            public InetAddress getAcceptorAddress() {
                return InetAddress.getLoopbackAddress();
            }

            @Override
            public int getAcceptorPort() {
                return port;
            }

            @Override
            public Duration getPeerDiscoveryInterval() {
                return Duration.ofSeconds(1);
            }

            @Override
            public Duration getTrackerQueryInterval() {
                return Duration.ofSeconds(1);
            }
        };

        return Bt.client()
                .config(config)
                .module(YourIPModule.class)
                .module(MockModule.class)
                .storage(new MockStorage())
                .torrent(() -> new MockTorrent())
                .build();
    }

}
