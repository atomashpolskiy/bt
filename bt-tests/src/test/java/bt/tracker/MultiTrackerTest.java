package bt.tracker;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MultiTrackerTest {

    private static final String trackerUrl1;
    private static final String trackerUrl2;
    private static final String trackerUrl3;
    private static final String backupUrl1;
    private static final String backupUrl2;

    static {
        trackerUrl1 = "http://tracker1.org/ann";
        trackerUrl2 = "http://tracker2.org/ann";
        trackerUrl3 = "http://tracker3.org/ann";
        backupUrl1 = "http://backup1.org/ann";
        backupUrl2 = "http://backup2.org/ann";
    }

    private AnnounceKey announceKey;
    private Torrent torrent;
    private ITrackerService trackerService;
    private List<Tracker> accessLog;
    private StoppableTracker tracker1, tracker2, tracker3, backup1, backup2;

    @Before
    public void setUp() {

        announceKey = new AnnounceKey(Arrays.asList(
                Arrays.asList(trackerUrl1, trackerUrl2, trackerUrl3),
                Arrays.asList(backupUrl1, backupUrl2)));

        torrent = mock(Torrent.class);
        when(torrent.getTorrentId()).thenReturn(TorrentId.fromBytes(new byte[20]));

        accessLog = new ArrayList<>();

        tracker1 = new StoppableTracker(trackerUrl1, torrent, accessLog::add);
        tracker2 = new StoppableTracker(trackerUrl2, torrent, accessLog::add);
        tracker3 = new StoppableTracker(trackerUrl3, torrent, accessLog::add);
        backup1 = new StoppableTracker(backupUrl1, torrent, accessLog::add);
        backup2 = new StoppableTracker(backupUrl2, torrent, accessLog::add);

        trackerService = mock(ITrackerService.class);
        when(trackerService.getTracker(trackerUrl1)).thenReturn(tracker1);
        when(trackerService.getTracker(trackerUrl2)).thenReturn(tracker2);
        when(trackerService.getTracker(trackerUrl3)).thenReturn(tracker3);
        when(trackerService.getTracker(backupUrl1)).thenReturn(backup1);
        when(trackerService.getTracker(backupUrl2)).thenReturn(backup2);
    }

    @Test
    public void testMultiTracker_FirstTrackerReachable() {

        MultiTracker tracker = new MultiTracker(trackerService, announceKey, false);

        tracker.request(torrent).start();
        assertLogHasTrackers(tracker1);
    }

    @Test
    public void testMultiTracker_UnreachableTrackers_MainTier() {

        MultiTracker tracker = new MultiTracker(trackerService, announceKey, false);

        tracker1.shutdown();
        tracker.request(torrent).start();
        assertLogHasTrackers(tracker1, tracker2);

        clearLog();
        tracker.request(torrent).query();
        assertLogHasTrackers(tracker2);

        clearLog();
        tracker2.shutdown();
        tracker.request(torrent).query();
        assertLogHasTrackers(tracker2, tracker1, tracker3);

        clearLog();
        tracker3.shutdown();
        tracker1.startup();
        tracker.request(torrent).query();
        assertLogHasTrackers(tracker3, tracker2, tracker1);

        clearLog();
        tracker.request(torrent).stop();
        assertLogHasTrackers(tracker1);
    }

    @Test
    public void testMultiTracker_UnreachableTrackers_Backups() {

        MultiTracker tracker = new MultiTracker(trackerService, announceKey, false);

        tracker1.shutdown();
        tracker2.shutdown();
        tracker3.shutdown();

        tracker.request(torrent).start();
        assertLogHasTrackers(tracker1, tracker2, tracker3, backup1);

        clearLog();
        backup1.shutdown();
        tracker.request(torrent).query();
        assertLogHasTrackers(tracker1, tracker2, tracker3, backup1, backup2);

        clearLog();
        tracker.request(torrent).stop();
        assertLogHasTrackers(tracker1, tracker2, tracker3, backup2);
    }

    private void assertLogHasTrackers(Tracker... trackersInVisitingOrder) {
        Tracker[] visitedTrackers = accessLog.toArray(new Tracker[accessLog.size()]);
        assertArrayEquals(trackersInVisitingOrder, visitedTrackers);
    }

    private void clearLog() {
        accessLog.clear();
    }

    private static class StoppableTracker implements Tracker {

        private Tracker instance;
        private final String url;
        private TrackerRequestBuilder requestBuilder;
        private boolean shutdown;

        public StoppableTracker(String url, Torrent torrent, Consumer<Tracker> accessLog) {

            instance = this;
            this.url = url;

            requestBuilder = new TrackerRequestBuilder(torrent.getTorrentId()) {
                @Override
                public TrackerResponse start() {
                    return logAndResponse();
                }

                @Override
                public TrackerResponse stop() {
                    return logAndResponse();
                }

                @Override
                public TrackerResponse complete() {
                    return logAndResponse();
                }

                @Override
                public TrackerResponse query() {
                    return logAndResponse();
                }

                private TrackerResponse logAndResponse() {
                    accessLog.accept(instance);
                    return shutdown? TrackerResponse.exceptional(new IOException("shutdown")) : TrackerResponse.ok();
                }
            };
        }

        @Override
        public TrackerRequestBuilder request(Torrent torrent) {
            return requestBuilder;
        }

        public void startup() {
            shutdown = false;
        }

        public void shutdown() {
            shutdown = true;
        }

        @Override
        public String toString() {
            return url;
        }
    }
}
