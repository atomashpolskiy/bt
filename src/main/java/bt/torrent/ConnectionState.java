package bt.torrent;

class ConnectionState {

    private boolean interested;
    private boolean peerInterested;
    private boolean choking;
    private boolean peerChoking;

    private long lastChokeStatusChanged;

    ConnectionState() {
        choking = true;
        peerChoking = true;
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
        lastChokeStatusChanged = System.currentTimeMillis();
    }

    public boolean isPeerChoking() {
        return peerChoking;
    }

    public void setPeerChoking(boolean peerChoking) {
        this.peerChoking = peerChoking;
    }

    public long lastChokeStatusChanged() {
        return lastChokeStatusChanged;
    }
}
