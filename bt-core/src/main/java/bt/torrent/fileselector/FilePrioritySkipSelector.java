package bt.torrent.fileselector;

import bt.metainfo.TorrentFile;

/**
 * @since 1.10
 */
@FunctionalInterface
public interface FilePrioritySkipSelector {
    FilePriority prioritize(TorrentFile file);
}
