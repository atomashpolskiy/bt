package bt.tracker.udp;

import java.io.IOException;
import java.io.OutputStream;

class ConnectRequest extends UdpTrackerMessage {

    private static final int CONNECT_TYPE_ID = 0;

    public ConnectRequest() {
        super(CONNECT_TYPE_ID);
    }

    @Override
    protected void writeBodyTo(OutputStream out) throws IOException {
        // do nothing
    }
}
