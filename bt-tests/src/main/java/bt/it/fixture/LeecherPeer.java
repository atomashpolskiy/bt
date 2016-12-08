package bt.it.fixture;

import bt.Bt;
import bt.data.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;

import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;

class LeecherPeer extends SwarmPeer {

    private BtClient handle;
    private File localRoot;
    private TorrentFiles files;

    LeecherPeer(File localRoot, TorrentFiles files, Supplier<Torrent> torrentSupplier, BtRuntime runtime) {
        super(runtime);

        this.handle = Bt.client(new FileSystemStorage(localRoot))
                .torrentSupplier(torrentSupplier)
                .attachToRuntime(runtime);

        this.localRoot = Objects.requireNonNull(localRoot);
        this.files = Objects.requireNonNull(files);

        files.createRoot(localRoot);
    }

    @Override
    public BtClient getHandle() {
        return handle;
    }

    @Override
    public boolean isSeeding() {
        // intentionally do not cache the result because
        // leecher may become seeder eventually
        return files.verifyFiles(localRoot);
    }
}
