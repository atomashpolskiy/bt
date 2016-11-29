package bt.tracker.udp;

import bt.metainfo.TorrentId;
import bt.net.PeerId;
import bt.tracker.udp.AnnounceRequest.EventType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class UdpMessageWorkerTest {

    @Rule
    public SingleClientUdpTracker tracker;
    @Rule
    public UdpTrackerConnection connection;
    @Rule
    public UdpTrackerTestExecutor client;

    private volatile int interval;
    private volatile int leechers;
    private volatile int seeders;

    @Before
    public void setUp() throws Exception {
        Random random = new Random(System.currentTimeMillis());
        this.interval = random.nextInt();
        this.leechers = random.nextInt();
        this.seeders = random.nextInt();

        this.tracker = new SingleClientUdpTracker(interval, leechers, seeders);
        this.connection = new UdpTrackerConnection(tracker);
        this.client = new UdpTrackerTestExecutor(tracker);
    }

    @Test
    public void testAnnounce() throws Exception {
        AnnounceRequest request = createAnnounceRequest(EventType.START);
        client.execute(
            () -> connection.getWorker().sendMessage(request, AnnounceResponseHandler.handler()),
            response -> {
                assertFalse(response.getError().isPresent());
                assertNull(response.getErrorMessage());
                assertNull(response.getWarningMessage());
                assertEquals(interval, response.getInterval());
                assertEquals(leechers, response.getLeecherCount());
                assertEquals(seeders, response.getSeederCount());
            });
    }

    private AnnounceRequest createAnnounceRequest(EventType eventType) {
        AnnounceRequest request = new AnnounceRequest();
        request.setTorrentId(TorrentId.fromBytes(new byte[20]));
        request.setPeerId(PeerId.fromBytes(new byte[20]));
        request.setEventType(eventType);
        return request;
    }
}
