/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.dht;

import bt.BtException;
import bt.data.DataDescriptor;
import bt.dht.stream.StreamAdapter;
import bt.event.EventSource;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.net.InetPeer;
import bt.net.InetPeerAddress;
import bt.net.Peer;
import bt.net.portmapping.PortMapper;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.service.LifecycleBinding;
import bt.torrent.TorrentRegistry;
import com.google.common.io.Files;
import com.google.inject.Inject;
import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.DHTLogger;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.PeerAddressDBItem;
import lbms.plugins.mldht.kad.tasks.PeerLookupTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import the8472.utils.io.NetMask;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static bt.net.portmapping.PortMapProtocol.UDP;

public class MldhtService implements DHTService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MldhtService.class);
    private static final DHTLogger DHT_LOGGER = createLogger();

    static {
        try {
            DHT.setLogger(DHT_LOGGER);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static DHTLogger createLogger() {
        return new DHTLogger() {
            @Override
            public void log(String message, LogLevel level) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("<" + level.name().toUpperCase() + "> " + message);
                }
            }

            @Override
            public void log(Throwable error, LogLevel level) {
                LOGGER.error("Unexpected DHT error", error);
            }
        };
    }

    private final Config config;
    private final DHTConfiguration dhtConfig;
    private final DHT dht;

    private final InetAddress localAddress;
    private final boolean useRouterBootstrap;
    private final Collection<InetPeerAddress> publicBootstrapNodes;
    private final Collection<InetPeerAddress> bootstrapNodes;
    private final Set<PortMapper> portMappers;
    private final TorrentRegistry torrentRegistry;

    private final AtomicBoolean started;

    @Inject
    public MldhtService(IRuntimeLifecycleBinder lifecycleBinder, Config config, DHTConfig dhtConfig,
                        Set<PortMapper> portMappers, TorrentRegistry torrentRegistry, EventSource eventSource) {
        this.dht = new DHT(dhtConfig.shouldUseIPv6() ? DHTtype.IPV6_DHT : DHTtype.IPV4_DHT);
        this.config = config;
        this.dhtConfig = toMldhtConfig(dhtConfig);
        this.localAddress = config.getAcceptorAddress();
        this.useRouterBootstrap = dhtConfig.shouldUseRouterBootstrap();
        this.publicBootstrapNodes = dhtConfig.getPublicBootstrapNodes();
        this.bootstrapNodes = dhtConfig.getBootstrapNodes();
        this.portMappers = portMappers;
        this.torrentRegistry = torrentRegistry;
        this.started = new AtomicBoolean(false);

        eventSource.onTorrentStarted(null, e -> onTorrentStarted(e.getTorrentId()));

        lifecycleBinder.onStartup(LifecycleBinding.bind(this::start).description("Initialize DHT facilities").async().build());
        lifecycleBinder.onShutdown("Shutdown DHT facilities", this::shutdown);
    }

    private DHTConfiguration toMldhtConfig(DHTConfig dhtConfig) {
        return new DHTConfiguration() {
            private final ConcurrentMap<InetAddress, Boolean> couldUseCacheMap = new ConcurrentHashMap<>();

            @Override
            public boolean isPersistingID() {
                return false;
            }

            @Override
            public Path getStoragePath() {
                return Files.createTempDir().toPath();
            }

            @Override
            public int getListeningPort() {
                return dhtConfig.getListeningPort();
            }

            @Override
            public boolean noRouterBootstrap() {
                return true;
            }

            @Override
            public boolean allowMultiHoming() {
                return false;
            }

            @Override
            public Predicate<InetAddress> filterBindAddress() {
                return address -> {
                    Boolean couldUse = couldUseCacheMap.get(address);
                    if (couldUse != null) {
                        return couldUse;
                    }
                    boolean bothAnyLocal = address.isAnyLocalAddress() && localAddress.isAnyLocalAddress();
                    couldUse = bothAnyLocal || localAddress.equals(address);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Filtering addresses to bind DHT server to.. Checking " + address + ".. Could use: " + couldUse);
                    }
                    couldUseCacheMap.put(address, couldUse);
                    return couldUse;
                };
            }
        };
    }

    private synchronized void start() {
        if (started.compareAndSet(false, true)) {
            try {
                dht.start(dhtConfig);
                if (useRouterBootstrap) {
                    publicBootstrapNodes.forEach(this::addNode);
                } else {
                    // assume that the environment is safe;
                    // might make this configuration more intelligent in future
                    dht.getNode().setTrustedNetMasks(Collections.singleton(NetMask.fromString("0.0.0.0/0")));
                }
                bootstrapNodes.forEach(this::addNode);
                mapPorts();

            } catch (SocketException e) {
                throw new BtException("Failed to start DHT", e);
            }
        }
    }

    private void mapPorts() {
        final int listeningPort = dhtConfig.getListeningPort();

        dht.getServerManager().getAllServers().forEach(s ->
                portMappers.forEach(m -> {
                    final InetAddress bindAddress = s.getBindAddress();
                    m.mapPort(listeningPort, bindAddress.toString(), UDP, "bt DHT");
                }));
    }

    private synchronized void onTorrentStarted(TorrentId torrentId) {
        if (started.get()) {
            torrentRegistry.getDescriptor(torrentId).ifPresent(td -> {
                DataDescriptor dd = td.getDataDescriptor();
                boolean seed = (dd != null) && (dd.getBitfield().getPiecesIncomplete() == 0);
                dht.getDatabase().store(new Key(torrentId.getBytes()),
                        PeerAddressDBItem.createFromAddress(config.getAcceptorAddress(), config.getAcceptorPort(), seed));
            });
        }
    }

    private synchronized void shutdown() {
        if (started.compareAndSet(true, false)) {
            dht.stop();
        }
    }

    @Override
    public Stream<Peer> getPeers(Torrent torrent) {
        return getPeers(torrent.getTorrentId());
    }

    @Override
    public Stream<Peer> getPeers(TorrentId torrentId) {
        try {
            dht.getServerManager().awaitActiveServer().get();
            final PeerLookupTask lookup = dht.createPeerLookup(torrentId.getBytes());
            final StreamAdapter<Peer> streamAdapter = new StreamAdapter<>();
            lookup.setResultHandler((k, p) -> {
                Peer peer = InetPeer.build(p.getInetAddress(), p.getPort());
                streamAdapter.addItem(peer);
            });
            lookup.addListener(t -> {
                streamAdapter.finishStream();
                if (torrentRegistry.isSupportedAndActive(torrentId)) {
                    torrentRegistry.getDescriptor(torrentId).ifPresent(td -> {
                        DataDescriptor dd = td.getDataDescriptor();
                        boolean seed = (dd != null) && (dd.getBitfield().getPiecesIncomplete() == 0);
                        dht.announce(lookup, seed, config.getAcceptorPort());
                    });
                }
            });
            dht.getTaskManager().addTask(lookup);
            return streamAdapter.stream();
        } catch (Throwable e) {
            LOGGER.error(String.format("Unexpected error in peer lookup: %s. See DHT log file for diagnostic information.",
                    e.getMessage()), e);
            BtException btex = new BtException(String.format("Unexpected error in peer lookup: %s. Diagnostics:\n%s",
                    e.getMessage(), getDiagnostics()), e);
            DHT_LOGGER.log(btex, LogLevel.Error);
            throw btex;
        }
    }

    @Override
    public void addNode(Peer node) {
        if (node.isPortUnknown()) {
            throw new IllegalArgumentException("Peer's port is unknown: " + node);
        }
        addNode(node.getInetAddress().getHostAddress(), node.getPort());
    }

    private void addNode(InetPeerAddress address) {
        addNode(address.getHostname(), address.getPort());
    }

    private void addNode(String hostname, int port) {
        dht.addDHTNode(hostname, port);
    }

    // TODO: add node by hostname/ipaddr and port ?

    private String getDiagnostics() {
        StringWriter sw = new StringWriter();
        dht.printDiagnostics(new PrintWriter(sw));
        return sw.toString();
    }
}
