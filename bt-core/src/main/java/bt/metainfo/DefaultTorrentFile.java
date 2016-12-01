package bt.metainfo;

import bt.BtException;

import java.util.List;

class DefaultTorrentFile implements TorrentFile {

    private long size;
    private List<String> pathElements;

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public List<String> getPathElements() {
        return pathElements;
    }

    public void setSize(long size) {
        if (size < 0) {
            throw new BtException("Invalid torrent file size: " + size);
        }
        this.size = size;
    }

    public void setPathElements(List<String> pathElements) {
        if (pathElements == null || pathElements.isEmpty()) {
            throw new BtException("Can't create torrent file without path");
        }
        this.pathElements = pathElements;
    }
}
