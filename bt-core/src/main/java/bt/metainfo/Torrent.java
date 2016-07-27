package bt.metainfo;

import java.net.URL;
import java.util.List;

public interface Torrent {

    /**
     * @return URL of the tracker.
     */
    URL getTrackerUrl();

    /**
     * @return Torrent ID.
     */
    TorrentId getTorrentId();

    /**
     * @return Suggested name for this torrent.
     */
    String getName();

    /**
     * @return Size of a transfer chunk, in bytes.
     */
    long getChunkSize();

    /**
     * @return Sequence of SHA-1 hashes of all chunks in this torrent.
     */
    Iterable<byte[]> getChunkHashes();

    /**
     * @return Total size of all chunks in this torrent, in bytes.
     */
    long getSize();

    /**
     * @return Information on the files contained in this torrent.
     */
    List<TorrentFile> getFiles();
}
