package bt.torrent.fileselector;

import bt.metainfo.TorrentFile;

@FunctionalInterface
public interface FilePrioritySelector {
    UpdatedFilePriority prioritize(TorrentFile file);
}
