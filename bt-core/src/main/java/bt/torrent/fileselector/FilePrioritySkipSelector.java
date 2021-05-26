package bt.torrent.fileselector;

import bt.metainfo.TorrentFile;

@FunctionalInterface
public interface FilePrioritySkipSelector {
    FilePriority prioritize(TorrentFile file);
}
