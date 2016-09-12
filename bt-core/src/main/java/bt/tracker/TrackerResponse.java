package bt.tracker;

import bt.net.Origin;
import bt.net.Peer;
import bt.tracker.CompactPeerInfo.AddressType;

import java.util.Optional;

public class TrackerResponse {

    public static TrackerResponse ok() {
        return new TrackerResponse();
    }

    public static TrackerResponse failure(String errorMessage) {
        return new TrackerResponse(errorMessage);
    }

    public static TrackerResponse exceptional(Throwable error) {
        return new TrackerResponse(error);
    }

    private final boolean success;
    private final Optional<Throwable> error;

    private String errorMessage;
    private String warningMessage;
    private int interval;
    private int minInterval;
    private byte[] trackerId;
    private int seederCount;
    private int leecherCount;

    private CompactPeerInfo peerInfo;

    protected TrackerResponse() {
        success = true;
        error = Optional.empty();
    }

    protected TrackerResponse(String errorMessage) {
        success = false;
        this.errorMessage = errorMessage;
        error = Optional.empty();
    }

    protected TrackerResponse(Throwable error) {
        success = false;
        this.error = Optional.of(error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public Optional<Throwable> getError() {
        return error;
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
        return peerInfo;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
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

    public void setPeers(byte[] peers, TrackerOrigin origin) {
        peerInfo = new CompactPeerInfo(peers, AddressType.IPV4, origin);
    }
}
