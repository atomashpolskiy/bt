package bt.tracker.udp;

interface UdpTrackerResponseHandler<T> {

    T onSuccess(byte[] data);

    T onError(String message);
}
