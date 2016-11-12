package bt.torrent.messaging;

import bt.protocol.Cancel;
import bt.protocol.Request;
import bt.torrent.data.BlockWrite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectionState {

    private volatile boolean interested;
    private volatile boolean peerInterested;
    private volatile boolean choking;
    private volatile boolean peerChoking;

    private volatile long downloaded;
    private volatile long uploaded;

    private Optional<Boolean> shouldChoke;
    private long lastChoked;

    private Set<Object> cancelledPeerRequests;

    // TODO: remove these
    private Optional<Boolean> mightSelectPieceForPeer;
    private Queue<Request> requestQueue;
    private Set<Object> pendingRequests;
    private Map<Object, BlockWrite> pendingWrites;
    private long lastCheckedAvailablePiecesForPeer;
    private long lastBuiltRequests;

    ConnectionState() {
        this.choking = true;
        this.peerChoking = true;
        this.shouldChoke = Optional.empty();
        this.cancelledPeerRequests = new HashSet<>();

        // TODO: remove these
        this.mightSelectPieceForPeer = Optional.empty();
        this.requestQueue = new LinkedBlockingQueue<>();
        this.pendingRequests = new HashSet<>();
        this.pendingWrites = new HashMap<>();
    }

    public boolean isInterested() {
        return interested;
    }

    public void setInterested(boolean interested) {
        this.interested = interested;
    }

    public boolean isPeerInterested() {
        return peerInterested;
    }

    public void setPeerInterested(boolean peerInterested) {
        this.peerInterested = peerInterested;
    }

    public boolean isChoking() {
        return choking;
    }

    public void setChoking(boolean choking) {
        this.choking = choking;
        this.shouldChoke = Optional.empty();
    }

    public void setShouldChoke(boolean shouldChoke) {
        this.shouldChoke = Optional.of(shouldChoke);
    }

    public Optional<Boolean> getShouldChoke() {
        return shouldChoke;
    }

    public long getLastChoked() {
        return lastChoked;
    }

    public void setLastChoked(long lastChoked) {
        this.lastChoked = lastChoked;
    }

    public boolean isPeerChoking() {
        return peerChoking;
    }

    public void setPeerChoking(boolean peerChoking) {
        this.peerChoking = peerChoking;
    }

    public void incrementDownloaded(long downloaded) {
        this.downloaded += downloaded;
    }

    public long getDownloaded() {
        return downloaded;
    }

    public void incrementUploaded(long uploaded) {
        this.uploaded += uploaded;
    }

    public long getUploaded() {
        return uploaded;
    }

    public Set<Object> getCancelledPeerRequests() {
        return cancelledPeerRequests;
    }

    public void onCancel(Cancel cancel) {
        cancelledPeerRequests.add(Mapper.mapper().buildKey(
                cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength()));
    }

    // TODO: Remove these
    public Optional<Boolean> getMightSelectPieceForPeer() {
        return mightSelectPieceForPeer;
    }
    public void setMightSelectPieceForPeer(Optional<Boolean> mightSelectPieceForPeer) {
        this.mightSelectPieceForPeer = mightSelectPieceForPeer;
    }
    public Queue<Request> getRequestQueue() {
        return requestQueue;
    }
    public void setRequestQueue(Queue<Request> requestQueue) {
        this.requestQueue = requestQueue;
    }
    public Set<Object> getPendingRequests() {
        return pendingRequests;
    }
    public void setPendingRequests(Set<Object> pendingRequests) {
        this.pendingRequests = pendingRequests;
    }
    public Map<Object, BlockWrite> getPendingWrites() {
        return pendingWrites;
    }
    public void setPendingWrites(Map<Object, BlockWrite> pendingWrites) {
        this.pendingWrites = pendingWrites;
    }
    public long getLastCheckedAvailablePiecesForPeer() {
        return lastCheckedAvailablePiecesForPeer;
    }
    public void setLastCheckedAvailablePiecesForPeer(long lastCheckedAvailablePiecesForPeer) {
        this.lastCheckedAvailablePiecesForPeer = lastCheckedAvailablePiecesForPeer;
    }
    public long getLastBuiltRequests() {
        return lastBuiltRequests;
    }
    public void setLastBuiltRequests(long lastBuiltRequests) {
        this.lastBuiltRequests = lastBuiltRequests;
    }
}
