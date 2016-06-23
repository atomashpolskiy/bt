package bt.tracker;

import bt.metainfo.Torrent;

public interface Tracker {

    TrackerRequestBuilder request(Torrent torrent);
}
