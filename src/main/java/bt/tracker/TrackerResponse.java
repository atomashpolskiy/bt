package bt.tracker;

import bt.BtException;
import bt.net.InetPeer;
import bt.net.Peer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TrackerResponse {

    private static final int ADDRESS_LENGTH = 4;
    private static final int PORT_LENGTH = 2;
    private static final int PEER_LENGTH = ADDRESS_LENGTH + PORT_LENGTH;

    private boolean success;
    private String errorMessage;
    private int interval;
    private int minInterval;
    private byte[] trackerId;
    private int seederCount;
    private int leecherCount;
    private byte[] peers;

    TrackerResponse(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getInterval() {
        return interval;
    }

    public int getMinInterval() {
        return minInterval;
    }

    public byte[] getTrackerId() {
        return trackerId;
    }

    public int getSeederCount() {
        return seederCount;
    }

    public int getLeecherCount() {
        return leecherCount;
    }

    public Iterable<Peer> getPeers() {

        return () -> new Iterator<Peer>() {

            private int i;

            @Override
            public boolean hasNext() {
                return i < peers.length;
            }

            @Override
            public Peer next() {

                if (!hasNext()) {
                    throw new NoSuchElementException("No more peers left");
                }

                int from, to;
                InetAddress inetAddress;
                int port;

                from = i; to = i = i + ADDRESS_LENGTH;
                try {
                    inetAddress = InetAddress.getByAddress(Arrays.copyOfRange(peers, from, to));
                } catch (UnknownHostException e) {
                    throw new BtException("Failed to get next peer", e);
                }

                from = to; to = i = i + PORT_LENGTH;
                port = (((peers[from] << 8) & 0xFF00) + (peers[to - 1] & 0x00FF));

                return new InetPeer(inetAddress, port);
            }
        };
    }

    void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    void setInterval(int interval) {
        this.interval = interval;
    }

    void setMinInterval(int minInterval) {
        this.minInterval = minInterval;
    }

    void setTrackerId(byte[] trackerId) {
        this.trackerId = trackerId;
    }

    void setSeederCount(int seederCount) {
        this.seederCount = seederCount;
    }

    void setLeecherCount(int leecherCount) {
        this.leecherCount = leecherCount;
    }

    public void setPeers(byte[] peers) {
        if (peers.length % PEER_LENGTH != 0) {
            throw new BtException("Invalid peers string -- length (" + peers.length
                    + ") is not divisible by " + PEER_LENGTH);
        }
        this.peers = peers;
    }
}
