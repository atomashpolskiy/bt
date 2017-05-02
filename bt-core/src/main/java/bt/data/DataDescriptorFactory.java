package bt.data;

import bt.data.digest.Digester;
import bt.metainfo.Torrent;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DataDescriptorFactory implements IDataDescriptorFactory {

    private Digester digester;
    private int transferBlockSize;
    private int numOfHashingThreads;

    public DataDescriptorFactory(Digester digester,
                                 int transferBlockSize,
                                 int numOfHashingThreads) {
        this.digester = digester;
        this.transferBlockSize = transferBlockSize;
        this.numOfHashingThreads = numOfHashingThreads;
    }

    @Override
    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DefaultDataDescriptor(storage, torrent, digester, transferBlockSize, numOfHashingThreads);
    }
}
