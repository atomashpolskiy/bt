package bt.tracker;

import bt.net.Peer;
import bt.tracker.CompactPeerInfo.AddressType;

import java.util.Optional;

/**
 * Tracker response.
 *
 * @since 1.0
 */
public class TrackerResponse {

    /**
     * @return Empty success response.
     * @since 1.0
     */
    public static TrackerResponse ok() {
        return new TrackerResponse();
    }

    /**
     * @return Failure response with the provided message.
     * @since 1.0
     */
    public static TrackerResponse failure(String errorMessage) {
        return new TrackerResponse(errorMessage);
    }

    /**
     * @return Exceptional response with the provided exception.
     *         Usually means that interaction with the tracker failed due to a I/O error,
     *         or a malformed response was received from the tracker.
     * @since 1.0
     */
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

    /**
     * Create an empty success response.
     *
     * @since 1.0
     */
    protected TrackerResponse() {
        success = true;
        error = Optional.empty();
    }

    /**
     * Create a failure response with the provided message.
     *
     * @since 1.0
     */
    protected TrackerResponse(String errorMessage) {
        success = false;
        this.errorMessage = errorMessage;
        error = Optional.empty();
    }

    /**
     * Create an exceptional response with the provided exception.
     * Usually means that interaction with the tracker failed due to a I/O error,
     * or a malformed response was received from the tracker.
     *
     * @since 1.0
     */
    protected TrackerResponse(Throwable error) {
        success = false;
        this.error = Optional.of(error);
    }

    /**
     * @return true if the tracker response has been received
     *         and it does not contain a failure message.
     * @since 1.0
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return Failure message, received from the tracker.
     * @since 1.0
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @return Warning message, received from the tracker.
     *         Note that a success response may also contain
     *         a warning message as additional info.
     * @since 1.0
     */
    public String getWarningMessage() {
        return warningMessage;
    }

    /**
     * @return Exception that happened during interaction with the tracker.
     * @since 1.0
     */
    public Optional<Throwable> getError() {
        return error;
    }

    /**
     * @return Preferred interval for querying peers from this tracker.
     * @since 1.0
     */
    public int getInterval() {
        return interval;
    }

    /**
     * @return Absolutely minimal interval for querying peers from this tracker.
     * @since 1.0
     */
    public int getMinInterval() {
        return minInterval;
    }

    /**
     * @return Binary tracker ID.
     * @since 1.0
     */
    public byte[] getTrackerId() {
        return trackerId;
    }

    /**
     * @return Number of seeders for the requested torrent.
     * @since 1.0
     */
    public int getSeederCount() {
        return seederCount;
    }

    /**
     * @return Number of leechers for the requested torrent.
     * @since 1.0
     */
    public int getLeecherCount() {
        return leecherCount;
    }

    /**
     * @return Collection of peers, that are active for the requested torrent.
     * @since 1.0
     */
    public Iterable<Peer> getPeers() {
        return peerInfo;
    }

    /**
     * @see #getWarningMessage()
     * @since 1.0
     */
    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    /**
     * @see #getInterval()
     * @since 1.0
     */
    void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * @see #getMinInterval()
     * @since 1.0
     */
    void setMinInterval(int minInterval) {
        this.minInterval = minInterval;
    }

    /**
     * @see #getTrackerId()
     * @since 1.0
     */
    void setTrackerId(byte[] trackerId) {
        this.trackerId = trackerId;
    }

    /**
     * @see #getSeederCount()
     * @since 1.0
     */
    void setSeederCount(int seederCount) {
        this.seederCount = seederCount;
    }

    /**
     * @see #getLeecherCount()
     * @since 1.0
     */
    void setLeecherCount(int leecherCount) {
        this.leecherCount = leecherCount;
    }

    /**
     * @see #getPeers()
     * @since 1.0
     */
    public void setPeers(byte[] peers) {
        peerInfo = new CompactPeerInfo(peers, AddressType.IPV4);
    }
}
