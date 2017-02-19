package bt.data;

import bt.metainfo.Torrent;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DataDescriptorFactory implements IDataDescriptorFactory {

    private int transferBlockSize;
    private int numOfHashingThreads;

    public DataDescriptorFactory(int transferBlockSize, int numOfHashingThreads) {
        this.transferBlockSize = transferBlockSize;
        this.numOfHashingThreads = numOfHashingThreads;
    }

    @Override
    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DefaultDataDescriptor(storage, torrent, transferBlockSize, numOfHashingThreads);
    }
}
