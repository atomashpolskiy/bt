package yourip.mock;

import bt.metainfo.TorrentId;
import bt.tracker.Tracker;
import bt.tracker.TrackerRequestBuilder;
import bt.tracker.TrackerResponse;

public class MockTracker implements Tracker {
    private static final String url = MockTrackerFactory.schema() + "://mock";

    public static String url() {
        return url;
    }

    @Override
    public TrackerRequestBuilder request(TorrentId torrentId) {
        return new TrackerRequestBuilder(torrentId) {
            @Override
            public TrackerResponse start() {
                return MockTrackerResponse.instance();
            }

            @Override
            public TrackerResponse stop() {
                return MockTrackerResponse.instance();
            }

            @Override
            public TrackerResponse complete() {
                return MockTrackerResponse.instance();
            }

            @Override
            public TrackerResponse query() {
                return MockTrackerResponse.instance();
            }
        };
    }
}
