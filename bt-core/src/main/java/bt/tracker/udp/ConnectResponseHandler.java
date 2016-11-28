package bt.tracker.udp;

import bt.BtException;
import bt.protocol.Protocols;

class ConnectResponseHandler implements UdpTrackerResponseHandler<Session> {

    private static final ConnectResponseHandler instance = new ConnectResponseHandler();

    public static ConnectResponseHandler handler() {
        return instance;
    }

    @Override
    public Session onSuccess(byte[] data) {
        return new Session(Protocols.readLong(data, 0));
    }

    @Override
    public Session onError(String message) {
        throw new BtException("Tracker returned error for connect request: " + message);
    }
}
