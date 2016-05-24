package bt.tracker;

import bt.Peer;

public class TrackerResponse {

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
        return null;
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
        this.peers = peers;
    }
}
