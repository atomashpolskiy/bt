package bt.it.fixture;

import bt.Bt;
import bt.runtime.BtRuntime;
import bt.data.file.FileSystemDataAccessFactory;
import bt.runtime.BtClient;
import bt.net.Peer;
import bt.service.IPeerRegistry;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class SwarmPeer implements Closeable {

    private File localRoot;
    private ITorrentFiles files;
    private BtRuntime runtime;

    private BtClient handle;

    SwarmPeer(File localRoot, ITorrentFiles files, BtRuntime runtime) {

        this.localRoot = Objects.requireNonNull(localRoot);
        this.files = Objects.requireNonNull(files);
        this.runtime = runtime;

        handle = Bt.client(runtime)
                .url(files.getMetainfoUrl())
                .build(new FileSystemDataAccessFactory(localRoot));
    }

    public BtClient getHandle() {
        return handle;
    }

    public Peer getPeer() {
        return runtime.service(IPeerRegistry.class).getLocalPeer();
    }

    public boolean hasFiles() {
        // intentionally do not cache the result because
        // leecher may become seeder eventually
        return files.verifyFiles(localRoot);
    }

    @Override
    public void close() throws IOException {
        runtime.shutdown();
    }
}
