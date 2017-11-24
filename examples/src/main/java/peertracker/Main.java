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

package peertracker;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.magnet.MagnetUri;
import bt.metainfo.TorrentId;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.Config;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.inject.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final FileSystem FS;
    private static final BtRuntime RUNTIME;
    private static final Map<TorrentId, PeerStats> STATS;
    private static final ScheduledExecutorService STATS_WRITER;

    private static final long STATS_DUMP_INTERVAL_SECONDS = 15;

    static {
        FS = Jimfs.newFileSystem(Configuration.unix());
        RUNTIME = createRuntime();
        STATS = new ConcurrentHashMap<>();
        STATS_WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                STATS_WRITER.shutdownNow();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                FS.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    /**
     * @param args First parameter must be a path to the file with magnet links;
     *             each line in the file should be a valid magnet link, e.g.:
     *
     *             magnet:?xt=urn:btih:f7a8e486ee974735e3295f896a6bc92d95c58f11
     *             magnet:?xt=urn:btih:f7a8e486ee974735e3295f896a6bc92d95c58f22
     *             ...
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            LOGGER.error("First parameter must be a path to the file with magnet links");
            System.exit(-1);
        }
        Collection<MagnetUri> magnets = new MagnetLinkFileReader().readFromFile(args[0]);

        List<BtClient> clients = magnets.stream()
            .peek(uri -> LOGGER.info("Creating client for info hash: {}", uri.getTorrentId()))
            .map(uri -> {
                attachPeerListener(RUNTIME, uri.getTorrentId());
                return createClient(RUNTIME, uri);
            })
            .collect(Collectors.toList());

        StatsDumper dumper = new StatsDumper(System.currentTimeMillis());
        LOGGER.info("Scheduling stats dump every {} seconds...", STATS_DUMP_INTERVAL_SECONDS);
        STATS_WRITER.scheduleWithFixedDelay(() -> dumper.dumpStats(STATS),
                STATS_DUMP_INTERVAL_SECONDS, STATS_DUMP_INTERVAL_SECONDS, TimeUnit.SECONDS);

        LOGGER.info("Starting clients...");
        List<CompletableFuture<?>> futures = clients.stream().map(BtClient::startAsync).collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
    }

    private static BtRuntime createRuntime() {
        Config config = new Config() {
            final int MAX_PEER_CONNECTIONS = 5000;

            @Override
            public int getMaxConcurrentlyActivePeerConnectionsPerTorrent() {
                return 0; // prevent downloading anything
            }

            @Override
            public int getMaxPeerConnections() {
                return MAX_PEER_CONNECTIONS;
            }

            @Override
            public int getMaxPeerConnectionsPerTorrent() {
                return MAX_PEER_CONNECTIONS;
            }
        };

        Module dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true;
            }
        });

        return BtRuntime.builder(config).autoLoadModules().module(dhtModule).build();
    }

    private static BtClient createClient(BtRuntime runtime, MagnetUri magnetUri) {
        Storage storage = new FileSystemStorage(FS.getPath("/" + UUID.randomUUID()));
        return Bt.client(runtime).magnet(magnetUri).storage(storage).initEagerly().build();
    }

    private static void attachPeerListener(BtRuntime runtime, TorrentId torrentId) {
        PeerStats perTorrentStats = STATS.computeIfAbsent(torrentId, it -> new PeerStats());
        runtime.getEventSource()
                .onPeerDiscovered(perTorrentStats::onPeerDiscovered)
                .onPeerConnected(perTorrentStats::onPeerConnected)
                .onPeerDisconnected(perTorrentStats::onPeerDisconnected)
                .onPeerBitfieldUpdated(perTorrentStats::onPeerBitfieldUpdated);
    }
}
