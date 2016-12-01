package bt.data;

import bt.metainfo.Torrent;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
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
