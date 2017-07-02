package bt.torrent;

import bt.data.DataDescriptor;

class DefaultTorrentDescriptor implements TorrentDescriptor {

    // !! this can be null in case with magnets (and in the beginning of processing) !!
    private volatile DataDescriptor dataDescriptor;

    private volatile boolean active;

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void start() {
        active = true;
    }

    @Override
    public void stop() {
        active = false;
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
