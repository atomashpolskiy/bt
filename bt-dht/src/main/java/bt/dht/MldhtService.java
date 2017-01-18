package bt.dht;

import bt.BtException;
import bt.metainfo.Torrent;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.service.IRuntimeLifecycleBinder;
import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.DHTLogger;
import lbms.plugins.mldht.kad.tasks.PeerLookupTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class MldhtService implements DHTService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MldhtService.class);

    static {
        try {
            DHT.setLogger(createLogger());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static DHTLogger createLogger() {

        return new DHTLogger() {
            @Override
            public void log(String message, LogLevel level) {
                switch (level) {
                    case Verbose: {
                        LOGGER.trace(message);
                    }
                    case Debug: {
                        LOGGER.debug(message);
                    }
                    case Info: {
                        LOGGER.info(message);
                    }
                    case Error: {
                        LOGGER.error(message);
                    }
                    case Fatal: {
                        LOGGER.error("<Fatal> " + message);
                    }
                }
            }

            @Override
            public void log(Throwable error, LogLevel level) {
                LOGGER.error("Unexpected DHT error", error);
            }
        };
    }

    private DHTConfiguration config;
    private DHT dht;

    public MldhtService(IRuntimeLifecycleBinder lifecycleBinder, DHTConfig config) {
        this.dht = new DHT(config.shouldUseIPv6()? DHTtype.IPV6_DHT : DHTtype.IPV4_DHT);
        this.config = toMldhtConfig(config);

        lifecycleBinder.onShutdown(this::shutdown);
    }

    private DHTConfiguration toMldhtConfig(DHTConfig config) {
        return new DHTConfiguration() {
            @Override
            public boolean isPersistingID() {
                return false;
            }

            @Override
            public Path getStoragePath() {
                return null;
            }

            @Override
            public int getListeningPort() {
                return config.getListeningPort();
            }

            @Override
            public boolean noRouterBootstrap() {
                return !config.shouldUseRouterBootstrap();
            }

            @Override
            public boolean allowMultiHoming() {
                return false;
            }
        };
    }

    @Override
    public synchronized void start() {
        if (!dht.isRunning()) {
            try {
                dht.start(config);
            } catch (SocketException e) {
                throw new BtException("Failed to start DHT", e);
            }
        }
    }

    @Override
    public synchronized void shutdown() {
        dht.stop();
    }

    @Override
    public Collection<Peer> getPeers(Torrent torrent) {
        PeerLookupTask lookup = dht.createPeerLookup(torrent.getTorrentId().getBytes());
        Set<Peer> peers = ConcurrentHashMap.newKeySet();
        lookup.setResultHandler((k, p) -> {
            peers.add(new InetPeer(p.getInetAddress(), p.getPort()));
        });
        dht.getTaskManager().addTask(lookup);

        while (!lookup.isFinished()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return peers;
    }
}
