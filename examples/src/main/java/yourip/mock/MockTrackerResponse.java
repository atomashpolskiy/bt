package yourip.mock;

import bt.net.Peer;
import bt.tracker.TrackerResponse;
import yourip.Main;

public class MockTrackerResponse extends TrackerResponse {
    private static final MockTrackerResponse instance = new MockTrackerResponse();

    public static MockTrackerResponse instance() {
        return instance;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public Iterable<Peer> getPeers() {
        return Main.peers();
    }
}
