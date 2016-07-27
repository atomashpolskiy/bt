package bt.tracker;

import bt.BtException;
import bt.metainfo.TorrentId;

import java.util.Objects;

public abstract class TrackerRequestBuilder {

    private TorrentId torrentId;

    private int uploaded;
    private int downloaded;
    private int left;

    protected TrackerRequestBuilder(TorrentId torrentId) {
        this.torrentId = Objects.requireNonNull(torrentId);
    }

    public abstract TrackerResponse start();

    public abstract TrackerResponse stop();

    public abstract TrackerResponse complete();

    public abstract TrackerResponse query();

    public TrackerRequestBuilder uploaded(int uploaded) {
        if (uploaded <= 0) {
            throw new BtException("Invalid uploaded value: " + uploaded);
        }
        this.uploaded = uploaded;
        return this;
    }

    public TrackerRequestBuilder downloaded(int downloaded) {
        if (downloaded <= 0) {
            throw new BtException("Invalid downloaded value: " + downloaded);
        }
        this.downloaded = downloaded;
        return this;
    }

    public TrackerRequestBuilder left(int left) {
        if (left <= 0) {
            throw new BtException("Invalid left value: " + left);
        }
        this.left = left;
        return this;
    }

    TorrentId getTorrentId() {
        return torrentId;
    }

    int getUploaded() {
        return uploaded;
    }

    int getDownloaded() {
        return downloaded;
    }

    int getLeft() {
        return left;
    }
}
