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

package bt.tracker.udp;

import bt.service.IRuntimeLifecycleBinder;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static org.mockito.Mockito.mock;

public class UdpTrackerConnection extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(UdpTrackerConnection.class);

    private final UdpMessageWorker worker;

    public UdpTrackerConnection(SingleClientUdpTracker tracker) {
        InetSocketAddress localAddress = new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0);
        this.worker = new UdpMessageWorker(localAddress, tracker.getServerAddress(), mock(IRuntimeLifecycleBinder.class));
        LOGGER.info("Established connection (local: {}, remote: {}", localAddress, tracker.getServerAddress());
    }

    public UdpMessageWorker getWorker() {
        return worker;
    }

    @Override
    protected void after() {
        try {
            worker.shutdown();
        } catch (Exception e) {
            // ignore
        }
    }
}
