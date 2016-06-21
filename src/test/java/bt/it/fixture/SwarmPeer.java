package bt.it.fixture;

import bt.Bt;
import bt.BtRuntime;
import bt.data.file.FileSystemDataAccessFactory;
import bt.torrent.TorrentHandle;

import java.io.File;
import java.util.Objects;

public class SwarmPeer {

    private File localRoot;
    private TorrentFiles files;
    private TorrentHandle handle;

    SwarmPeer(File localRoot, TorrentFiles files, BtRuntime runtime) {

        this.localRoot = Objects.requireNonNull(localRoot);
        this.files = Objects.requireNonNull(files);

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
}
