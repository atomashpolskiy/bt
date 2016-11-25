package bt.data;

import bt.metainfo.Torrent;

public class DataDescriptorFactory implements IDataDescriptorFactory {

    private int transferBlockSize;

    public DataDescriptorFactory(int transferBlockSize) {
        this.transferBlockSize = transferBlockSize;
    }

    @Override
    public IDataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DataDescriptor(storage, torrent, transferBlockSize);
    }
}
