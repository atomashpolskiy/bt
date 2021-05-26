package bt.torrent.callbacks;

import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;

/**
 * This is a functional interface to make a callback when a file downloaded in a torrent is complete
 */
@FunctionalInterface
public interface FileDownloadCompleteCallback {
    void fileDownloadCompleted(Torrent torrent, TorrentFile tf, Storage s);
}
