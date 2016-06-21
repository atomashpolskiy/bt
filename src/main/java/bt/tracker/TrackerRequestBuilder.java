package bt.tracker;

import bt.BtException;

public abstract class TrackerRequestBuilder {

    private byte[] infoHash;

    private int uploaded;
    private int downloaded;
    private int left;

    protected TrackerRequestBuilder(byte[] infoHash) {
        this.infoHash = infoHash;
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

    byte[] getInfoHash() {
        return infoHash;
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
