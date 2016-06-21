package bt.torrent;

public interface IConnectionState {
    boolean isInterested();

    void setInterested(boolean interested);

    boolean isPeerInterested();

    void setPeerInterested(boolean peerInterested);

    boolean isChoking();

    void setChoking(boolean choking);

    boolean isPeerChoking();

    void setPeerChoking(boolean peerChoking);
}
