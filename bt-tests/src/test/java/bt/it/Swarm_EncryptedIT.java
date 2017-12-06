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

package bt.it;

import bt.it.fixture.BaseBtTest;
import bt.it.fixture.SharedTrackerModule;
import bt.it.fixture.Swarm;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.BtClient;
import bt.runtime.Config;
import org.junit.Rule;
import org.junit.Test;

import java.net.InetAddress;
import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class Swarm_EncryptedIT extends BaseBtTest {

    private static final int NUMBER_OF_SEEDERS = 5;

    private static final Config CONFIG = new Config() {
        @Override
        public InetAddress getAcceptorAddress() {
            return InetAddress.getLoopbackAddress();
        }

        @Override
        public Duration getTrackerQueryInterval() {
            return Duration.ofSeconds(5);
        }

        @Override
        public EncryptionPolicy getEncryptionPolicy() {
            return EncryptionPolicy.REQUIRE_ENCRYPTED;
        }

        @Override
        public int getTransferBlockSize() {
            return 1024;
        }

        @Override
        public int getMaxTransferBlockSize() {
            // trigger buffer compaction
            return 4*1024;
        }
    };

    @Rule
    public Swarm swarm = buildSwarm()
            .config(CONFIG)
            .seeders(NUMBER_OF_SEEDERS)
            .leechers(NUMBER_OF_SEEDERS)
            .module(new SharedTrackerModule())
            .useInMemoryFileSystem()
            .build();

    @Test
    public void testSwarm_OneSeederOneLeecher() {
        BtClient seeder = swarm.getSeederHandles().iterator().next();
        BtClient leecher = swarm.getLeecherHandles().iterator().next();

        seeder.startAsync();
        leecher.startAsync().join();
        seeder.stop();

        assertEquals(NUMBER_OF_SEEDERS + 1, swarm.getSeeders().size());
        assertEquals(NUMBER_OF_SEEDERS - 1, swarm.getLeechers().size());
    }
}
