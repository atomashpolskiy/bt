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

package bt.it.fixture;

import bt.metainfo.Torrent;
import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;

import java.nio.file.Path;
import java.util.PrimitiveIterator;
import java.util.function.Supplier;
import java.util.stream.IntStream;

class DefaultSwarmPeerFactory implements SwarmPeerFactory {

    private Path root;
    private TorrentFiles torrentFiles;
    private Supplier<Torrent> torrentSupplier;
    private PrimitiveIterator.OfInt ports;

    DefaultSwarmPeerFactory(Path root, TorrentFiles torrentFiles, Supplier<Torrent> torrentSupplier, int startingPort) {
        this.root = root;
        this.torrentFiles = torrentFiles;
        this.torrentSupplier = torrentSupplier;
        this.ports = IntStream.range(startingPort, 65536).iterator();
    }

    @Override
    public SwarmPeer createSeeder(BtRuntimeBuilder runtimeBuilder) {
        int port = ports.next();
        BtRuntime runtime = createRuntime(runtimeBuilder, port);
        return new SeederPeer(createLocalRoot(port), torrentFiles, torrentSupplier, runtime);
    }

    @Override
    public SwarmPeer createLeecher(BtRuntimeBuilder runtimeBuilder) {
        return createLeecher(runtimeBuilder, false);
    }

    @Override
    public SwarmPeer createMagnetLeecher(BtRuntimeBuilder runtimeBuilder) {
        return createLeecher(runtimeBuilder, true);
    }

    private SwarmPeer createLeecher(BtRuntimeBuilder runtimeBuilder, boolean useMagnet) {
        int port = ports.next();
        BtRuntime runtime = createRuntime(runtimeBuilder, port);
        return new LeecherPeer(createLocalRoot(port), torrentFiles, torrentSupplier, runtime, useMagnet, true);
    }

    private BtRuntime createRuntime(BtRuntimeBuilder runtimeBuilder, int port) {
        Config config = runtimeBuilder.getConfig();
        config.setAcceptorPort(port);
        return runtimeBuilder.build();
    }

    private Path createLocalRoot(int port) {
        return root.resolve(String.valueOf(port));
    }
}
