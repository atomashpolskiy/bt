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
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.inject.Module;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Swarm builder.
 *
 * @since 1.0
 */
public class SwarmBuilder {

    private Config config;
    private Collection<Module> modules;

    private String name;

    private int startingPort;
    private int seedersCount;
    private int leechersCount;
    private int magnetLeechersCount;
    private TorrentFiles torrentFiles;
    private Supplier<Torrent> torrentSupplier;

    private Supplier<Path> swarmFileRootSupplier;
    private Collection<Closeable> swarmResources;

    SwarmBuilder(String name,
                 Path defaultFsRelativeRoot,
                 TorrentFiles torrentFiles) {
        this.config = new Config();
        this.modules = new ArrayList<>();

        this.name = name;
        this.startingPort = 6891;
        this.torrentFiles = torrentFiles;
        this.torrentSupplier = () -> {throw new RuntimeException("No torrent provided");};

        Supplier<Path> supplier = () -> defaultFsRelativeRoot.resolve(name);
        this.swarmFileRootSupplier = supplier;
        this.swarmResources = Collections.singletonList(() -> deleteRecursive(supplier.get()));
    }

    private void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        if (Files.isDirectory(path)) {
            List<Path> children = Files.list(path).collect(Collectors.toList());
            for (Path child : children) {
                deleteRecursive(child);
            }
        }

        Files.delete(path);
    }

    /**
     * Provide a custom Bt configuration, that will be used as a template for creating swarm participants.
     *
     * @param config Custom config
     * @since 1.0
     */
    public SwarmBuilder config(Config config) {
        this.config = config;
        return this;
    }

    /**
     * Provide a custom feature, that will be used by all swarm participants.
     *
     * @param module Custom feature
     * @since 1.0
     */
    public SwarmBuilder module(Module module) {
        this.modules.add(module);
        return this;
    }

    /**
     * Set number of seeders in this swarm.
     *
     * @param count Number of seeders
     * @since 1.0
     */
    public SwarmBuilder seeders(int count) {
        if (count < 0) {
            throw new RuntimeException("Invalid seeders count: " + count);
        }
        this.seedersCount = count;
        return this;
    }

    /**
     * Set number of leechers in this swarm.
     *
     * @param count Number of leechers, that will use .torrent file to bootstrap
     * @since 1.0
     */
    public SwarmBuilder leechers(int count) {
        if (count < 0) {
            throw new RuntimeException("Invalid leechers count: " + count);
        }
        this.leechersCount = count;
        this.magnetLeechersCount = 0;
        return this;
    }

    /**
     * Set number of leechers in this swarm.
     *
     * @param count Number of leechers, that will use .torrent file to bootstrap
     * @param magnetCount Number of leechers, that will use magnet link
     * @since 1.3
     */
    public SwarmBuilder leechers(int count, int magnetCount) {
        if (count < 0 || magnetCount < 0) {
            throw new RuntimeException("Invalid leechers count; standard: " + count + ", magnet: " + magnetCount);
        }
        this.leechersCount = count;
        this.magnetLeechersCount = magnetCount;
        return this;
    }

    /**
     * Override the initial port for this swarm. First swarm participant is assigned to this port,
     * each subsequently created swarm participant is assigned to port #{@code previous participant's port + 1}.
     *
     * <p>Default value is 6891.
     *
     * @param startingPort Number of seeders
     * @since 1.0
     */
    public SwarmBuilder startingPort(int startingPort) {
        this.startingPort = startingPort;
        return this;
    }

    /**
     * Provide a custom torrent supplier.
     *
     * @param torrentSupplier Torrent supplier
     * @since 1.0
     */
    public SwarmBuilder torrentSupplier(Supplier<Torrent> torrentSupplier) {
        this.torrentSupplier = torrentSupplier;
        return this;
    }

    /**
     * Use in-memory file system implementation.
     *
     * @since 1.3
     */
    public SwarmBuilder useInMemoryFileSystem() {
        FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix());
        this.swarmFileRootSupplier = () -> jimfs.getRootDirectories().iterator().next().resolve(name);
        this.swarmResources = Collections.singletonList(jimfs);
        return this;
    }

    /**
     * Build swarm.
     *
     * @since 1.0
     */
    public Swarm build() {
        SwarmPeerFactory swarmPeerFactory = new DefaultSwarmPeerFactory(getFileRoot(), torrentFiles, torrentSupplier, startingPort);
        BtRuntimeFactory runtimeFactory = createRuntimeFactory(modules);

        Collection<SwarmPeer> peers = new ArrayList<>(seedersCount + leechersCount + 1);
        for (int i = 0; i < seedersCount; i++) {
            peers.add(swarmPeerFactory.createSeeder(newRuntimeBuilder(runtimeFactory)));
        }
        for (int i = 0; i < leechersCount; i++) {
            peers.add(swarmPeerFactory.createLeecher(newRuntimeBuilder(runtimeFactory)));
        }
        for (int i = 0; i < magnetLeechersCount; i++) {
            peers.add(swarmPeerFactory.createMagnetLeecher(newRuntimeBuilder(runtimeFactory)));
        }

        return new Swarm(swarmResources, peers);
    }

    private Path getFileRoot() {
        return swarmFileRootSupplier.get();
    }

    private BtRuntimeFactory createRuntimeFactory(Collection<Module> modules) {
        return new BtRuntimeFactory(modules);
    }

    private BtRuntimeBuilder newRuntimeBuilder(BtRuntimeFactory runtimeFactory) {
        Config config = cloneConfig(this.config);
        return runtimeFactory.builder(config);
    }

    private Config cloneConfig(Config config) {
        return new Config(config);
    }
}
