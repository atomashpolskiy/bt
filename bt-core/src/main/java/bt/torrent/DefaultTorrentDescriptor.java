package bt.torrent;

import bt.data.DataDescriptor;
import bt.event.EventSink;
import bt.metainfo.TorrentId;

class DefaultTorrentDescriptor implements TorrentDescriptor {

    private final TorrentId torrentId;
    private final EventSink eventSink;

    // !! this can be null in case with magnets (and in the beginning of processing) !!
    private volatile DataDescriptor dataDescriptor;

    private volatile boolean active;

    DefaultTorrentDescriptor(TorrentId torrentId, EventSink eventSink) {
        this.torrentId = torrentId;
        this.eventSink = eventSink;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public synchronized void start() {
        active = true;
        eventSink.fireTorrentStarted(torrentId);
    }

    @Override
    public synchronized void stop() {
        active = false;
        eventSink.fireTorrentStopped(torrentId);
    }

    @Override
    public void complete() {
        // do nothing
        // TODO: should this be deprecated in TorrentDescriptor interface?
    }

    @Override
    public DataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }

    void setDataDescriptor(DataDescriptor dataDescriptor) {
        this.dataDescriptor = dataDescriptor;
    }
}
