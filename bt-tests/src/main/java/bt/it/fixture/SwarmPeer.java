package bt.it.fixture;

import bt.Bt;
import bt.BtRuntime;
import bt.data.file.FileSystemDataAccessFactory;
import bt.torrent.TorrentHandle;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class SwarmPeer implements Closeable {

    private File localRoot;
    private ITorrentFiles files;
    private BtRuntime runtime;

    private TorrentHandle handle;

    SwarmPeer(File localRoot, ITorrentFiles files, BtRuntime runtime) {

        this.localRoot = Objects.requireNonNull(localRoot);
        this.files = Objects.requireNonNull(files);
        this.runtime = runtime;

        handle = Bt.torrentWorker(runtime)
                .metainfoUrl(files.getMetainfoUrl())
                .build(new FileSystemDataAccessFactory(localRoot));
    }

    public TorrentHandle getHandle() {
        return handle;
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
