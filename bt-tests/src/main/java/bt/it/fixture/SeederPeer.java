package bt.it.fixture;

import bt.metainfo.Torrent;
import bt.runtime.BtRuntime;

import java.nio.file.Path;
import java.util.function.Supplier;

class SeederPeer extends LeecherPeer {

    SeederPeer(Path localRoot, TorrentFiles files, Supplier<Torrent> torrentSupplier, BtRuntime runtime) {
        super(localRoot, files, torrentSupplier, runtime, false);
        files.createFiles(localRoot);
    }
}
