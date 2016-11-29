package bt.tracker.udp;

import bt.service.IRuntimeLifecycleBinder;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static org.mockito.Mockito.mock;

public class UdpTrackerConnection extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(UdpTrackerConnection.class);

    private final UdpMessageWorker worker;

    public UdpTrackerConnection(SingleClientUdpTracker tracker) {
        InetSocketAddress localAddress = new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0);
        this.worker = new UdpMessageWorker(localAddress, tracker.getServerAddress(), mock(IRuntimeLifecycleBinder.class));
        LOGGER.info("Established connection (local: {}, remote: {}", localAddress, tracker.getServerAddress());
    }

    public UdpMessageWorker getWorker() {
        return worker;
    }

    @Override
    protected void after() {
        try {
            worker.shutdown();
        } catch (Exception e) {
            // ignore
        }
    }
}
