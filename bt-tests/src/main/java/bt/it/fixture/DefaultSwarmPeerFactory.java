package bt.it.fixture;

import bt.metainfo.Torrent;
import bt.runtime.BtRuntime;
import bt.runtime.BtRuntimeBuilder;
import bt.runtime.Config;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.PrimitiveIterator;
import java.util.function.Supplier;
import java.util.stream.IntStream;

class DefaultSwarmPeerFactory implements SwarmPeerFactory {

    private File root;
    private TorrentFiles torrentFiles;
    private Supplier<Torrent> torrentSupplier;
    private PrimitiveIterator.OfInt ports;

    DefaultSwarmPeerFactory(File root, TorrentFiles torrentFiles, Supplier<Torrent> torrentSupplier, int startingPort) {
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
        int port = ports.next();
        BtRuntime runtime = createRuntime(runtimeBuilder, port);
        return new LeecherPeer(createLocalRoot(port), torrentFiles, torrentSupplier, runtime);
    }

    private BtRuntime createRuntime(BtRuntimeBuilder runtimeBuilder, int port) {
        Config config = runtimeBuilder.getConfig();
        config.setAcceptorAddress(localhostAddress());
        config.setAcceptorPort(port);
        return runtimeBuilder.build();
    }

    protected static InetAddress localhostAddress() {
        try {
            return Inet4Address.getLocalHost();
        } catch (UnknownHostException e) {
            // not going to happen
            throw new RuntimeException("Unexpected error", e);
        }
    }

    private File createLocalRoot(int port) {
        return new File(root, String.valueOf(port));
    }
}
