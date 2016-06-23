package bt.it.fixture;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Swarm extends ExternalResource {

    public static SwarmBuilder builder(File root, BtTestRuntimeFactory runtimeFactory) {
        return new SwarmBuilder(root, runtimeFactory);
    }

    public static SwarmBuilder builder(File root, BtTestRuntimeFactory runtimeFactory, Collection<BtTestRuntimeFeature> features) {
        return new SwarmBuilder(root, runtimeFactory, features);
    }

    private File root;
    private Collection<SwarmPeer> peers;

    private Swarm(File root, Collection<SwarmPeer> peers) {
        this.root = root;
        this.peers = peers;
    }

    public void shutdown() {
        deleteRecursive(root);
    }

    public List<SwarmPeer> getSeeders() {
        return peers.stream().filter(SwarmPeer::hasFiles).collect(Collectors.toList());
    }

    public List<SwarmPeer> getLeechers() {
        return peers.stream().filter(peer -> !peer.hasFiles()).collect(Collectors.toList());
    }

    private void deleteRecursive(File file) {

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        if (!file.delete()) {
            throw new RuntimeException("Failed to delete file: " + file.getPath());
        }
    }

    @Override
    protected void after() {
        shutdown();
    }

    public static class SwarmBuilder {

        private File root;
        private BtTestRuntimeFactory runtimeFactory;
        private Collection<BtTestRuntimeFeature> features;

        private int seedersCount;
        private int leechersCount;
        private int startingPort;
        private TorrentFiles files;

        private SwarmBuilder(File root, BtTestRuntimeFactory runtimeFactory) {
            this.root = Objects.requireNonNull(root);
            this.runtimeFactory = Objects.requireNonNull(runtimeFactory);
            startingPort = 6891;
        }

        private SwarmBuilder(File root, BtTestRuntimeFactory runtimeFactory, Collection<BtTestRuntimeFeature> features) {
            this(root, runtimeFactory);
            this.features = Objects.requireNonNull(features);
        }

        public SwarmBuilder seeders(int count) {
            if (count < 0) {
                throw new RuntimeException("Invalid seeders count: " + count);
            }
            this.seedersCount = count;
            return this;
        }

        public SwarmBuilder leechers(int count) {
            if (count < 0) {
                throw new RuntimeException("Invalid leechers count: " + count);
            }
            this.leechersCount = count;
            return this;
        }

        public SwarmBuilder startingPort(int startingPort) {
            checkPort(startingPort);
            this.startingPort = startingPort;
            return this;
        }

        public SwarmBuilder files(TorrentFiles files) {
            this.files = files;
            return this;
        }

        public Swarm build() {
            checkPort(startingPort);
            Objects.requireNonNull(files);

            int port = startingPort;

            Collection<SwarmPeer> peers = new ArrayList<>(seedersCount + leechersCount + 1);
            for (int i = 0; i < seedersCount; i++) {
                peers.add(createSeeder(port, new File(root, String.valueOf(port)), files));
                port++;
            }
            for (int i = 0; i < leechersCount; i++) {
                peers.add(createLeecher(port, new File(root, String.valueOf(port)), files));
                port++;
            }

            return new Swarm(root, peers);
        }

        private static void checkPort(int port) {
            if (port <= 1024 || port >= 65535) {
                throw new RuntimeException("Invalid port: " + port);
            }
        }

        private SwarmPeer createSeeder(int port, File localRoot, TorrentFiles files) {
            files.createFiles(localRoot);
            return createLeecher(port, localRoot, files);
        }

        private SwarmPeer createLeecher(int port, File localRoot, TorrentFiles files) {
            files.createRoot(localRoot);
            BtTestRuntimeBuilder runtimeBuilder = runtimeFactory.buildRuntime(localhostAddress(), port);
            features.forEach(runtimeBuilder::feature);
            return new SwarmPeer(localRoot, files, runtimeBuilder.build());
        }

        protected static InetAddress localhostAddress() {
            try {
                return Inet4Address.getLocalHost();
            } catch (UnknownHostException e) {
                // not going to happen
                throw new RuntimeException("Unexpected error", e);
            }
        }
    }
}
