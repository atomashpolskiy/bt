/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

package datasharing;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.net.InetPeerAddress;
import bt.runtime.BtClient;
import bt.runtime.Config;
import com.google.inject.Module;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;

public class Leecher {

    public static void main(String[] args) throws Exception {
        Config config = new Config() {
            @Override
            public int getNumOfHashingThreads() {
                return Runtime.getRuntime().availableProcessors() * 2;
            }

            @Override
            public int getAcceptorPort() {
                return 6991;
            }
        };

        Module dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public Collection<InetPeerAddress> getBootstrapNodes() {
                return Collections.singleton(new InetPeerAddress(config.getAcceptorAddress().getHostAddress(), Seeder.PORT));
            }
        });

        Storage storage = new FileSystemStorage(Paths.get("/Users", "sadpotato", "Downloads", "seeder"));
        BtClient client = Bt.client()
                .config(config)
                .storage(storage)
                .magnet("magnet:?xt=urn:btih:5dbde2ccce0bcdd9d9ca30b2db3c1bb51b9b7410")
                .autoLoadModules()
                .module(dhtModule)
                .stopWhenDownloaded()
                .build();
        System.err.println("Starting leecher...");
        long t0 = System.currentTimeMillis();
        client.startAsync(state -> {
            System.err.println("Peers: " + state.getConnectedPeers().size() + "; Downloaded: " + (((double)state.getPiecesComplete()) / state.getPiecesTotal()) * 100 + "%");
        }, 1000).join();
        System.err.println("Done in " + Duration.ofMillis(System.currentTimeMillis() - t0));
    }
}
