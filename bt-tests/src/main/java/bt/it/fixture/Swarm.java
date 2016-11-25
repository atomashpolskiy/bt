package bt.it.fixture;

import bt.BtException;
import bt.runtime.BtRuntime;
import bt.data.Storage;
import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.data.IDataDescriptor;
import bt.data.IDataDescriptorFactory;
import bt.metainfo.Torrent;
import bt.runtime.Config;
import bt.torrent.Bitfield;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Swarm extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(Swarm.class);

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
        peers.forEach(peer -> {
            try {
                peer.close();
            } catch (IOException e) {
                LOGGER.warn("Error on swarm shutdown", e);
            }
        });
        deleteRecursive(root);
    }

    public List<SwarmPeer> getSeeders() {
        return peers.stream().filter(SwarmPeer::hasFiles).collect(Collectors.toList());
    }

    public List<SwarmPeer> getLeechers() {
        return peers.stream().filter(peer -> !peer.hasFiles()).collect(Collectors.toList());
    }

    private void deleteRecursive(File file) {

        if (file.exists()) {
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
    }

    @Override
    protected void after() {
        shutdown();
    }

    public static class SwarmBuilder {

        private static final URL NOFILES_METAINFO_URL = BaseBtTest.class.getResource("no-files.torrent");

        private File root;
        private BtTestRuntimeFactory runtimeFactory;
        private Config config;
        private Collection<BtTestRuntimeFeature> features;

        private boolean withoutFiles;

        private int seedersCount;
        private int leechersCount;
        private int startingPort;
        private ITorrentFiles files;

        private SwarmBuilder(File root, BtTestRuntimeFactory runtimeFactory) {
            this.root = Objects.requireNonNull(root);
            this.runtimeFactory = Objects.requireNonNull(runtimeFactory);
            this.config = new Config();
            this.startingPort = 6891;
        }

        private SwarmBuilder(File root, BtTestRuntimeFactory runtimeFactory, Collection<BtTestRuntimeFeature> features) {
            this(root, runtimeFactory);
            this.features = Objects.requireNonNull(features);
        }

        public SwarmBuilder config(Config config) {
            this.config = config;
            return this;
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

        public SwarmBuilder withoutFiles() {
            this.files = null;
            withoutFiles = true;
            return this;
        }

        public SwarmBuilder files(ITorrentFiles files) {
            this.files = files;
            withoutFiles = false;
            return this;
        }

        public Swarm build() {
            checkPort(startingPort);

            if (!withoutFiles) {
                Objects.requireNonNull(files);
            }

            int port = startingPort;

            Collection<SwarmPeer> peers = new ArrayList<>(seedersCount + leechersCount + 1);
            for (int i = 0; i < seedersCount; i++) {
                peers.add(createSeeder(port, new File(root, String.valueOf(port)), withoutFiles? mockSeederFiles() : files));
                port++;
            }
            for (int i = 0; i < leechersCount; i++) {
                peers.add(createLeecher(port, new File(root, String.valueOf(port)), withoutFiles? mockLeecherFiles() : files));
                port++;
            }

            return new Swarm(root, peers);
        }

        private static void checkPort(int port) {
            if (port <= 1024 || port >= 65535) {
                throw new RuntimeException("Invalid port: " + port);
            }
        }

        private SwarmPeer createSeeder(int port, File localRoot, ITorrentFiles files) {
            files.createFiles(localRoot);
            files.createRoot(localRoot);
            return new SwarmPeer(localRoot, files, createPeerRuntime(port));
        }

        private SwarmPeer createLeecher(int port, File localRoot, ITorrentFiles files) {
            files.createRoot(localRoot);
            return new SwarmPeer(localRoot, files, createPeerRuntime(port));
        }

        private BtRuntime createPeerRuntime(int port) {

            BtTestRuntimeBuilder runtimeBuilder = runtimeFactory.buildRuntime(config, localhostAddress(), port);
            features.forEach(runtimeBuilder::feature);

            if (withoutFiles) {
                runtimeBuilder.feature((configuration, builder) ->
                        builder.module(binder ->
                                binder.bind(IDataDescriptorFactory.class).to(MockDataDescriptorFactory.class)));
            }
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

        private static ITorrentFiles mockSeederFiles() {
            return mockFiles(true);
        }

        private static ITorrentFiles mockLeecherFiles() {
            return mockFiles(false);
        }

        private static ITorrentFiles mockFiles(boolean seeder) {
            return new ITorrentFiles() {
                @Override
                public URL getMetainfoUrl() {
                    return NOFILES_METAINFO_URL;
                }

                @Override
                public void createFiles(File root) {
                    // do nothing
                }

                @Override
                public void createRoot(File root) {
                    // do nothing
                }

                @Override
                public boolean verifyFiles(File root) {
                    return seeder; // for leechers this always returns false
                }
            };
        }
    }

    private static class MockDataDescriptorFactory implements IDataDescriptorFactory {

        @Override
        public IDataDescriptor createDescriptor(Torrent torrent, Storage storage) {
            return new IDataDescriptor() {

                private List<IChunkDescriptor> descriptors;
                private Bitfield bitfield;

                {
                    descriptors = Collections.singletonList(new IChunkDescriptor() {
                        @Override
                        public DataStatus getStatus() {
                            return DataStatus.VERIFIED;
                        }

                        @Override
                        public long getSize() {
                            return 8;
                        }

                        @Override
                        public long getBlockSize() {
                            return 1;
                        }

                        @Override
                        public byte[] getBitfield() {
                            return new byte[]{-1};
                        }

                        @Override
                        public byte[] readBlock(long offset, int length) {
                            throw new BtException("Unexpected read request");
                        }

                        @Override
                        public void writeBlock(byte[] block, long offset) {
                            throw new BtException("Unexpected write request");
                        }

                        @Override
                        public boolean verify() {
                            return true;
                        }
                    });

                    bitfield = new Bitfield(descriptors);
                }

                @Override
                public List<IChunkDescriptor> getChunkDescriptors() {
                    return descriptors;
                }

                @Override
                public Bitfield getBitfield() {
                    return bitfield;
                }

                @Override
                public void close() {
                    // do nothing
                }
            };
        }
    }
}
