package bt.processor;

import bt.metainfo.TorrentId;

public interface ProcessingContext {

    TorrentId getTorrentId();
}
