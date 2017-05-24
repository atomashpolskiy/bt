package bt.data;

import bt.metainfo.Torrent;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DataDescriptorFactory implements IDataDescriptorFactory {

    private ChunkVerifier verifier;
    private int transferBlockSize;

    public DataDescriptorFactory(ChunkVerifier verifier,
                                 int transferBlockSize) {
        this.verifier = verifier;
        this.transferBlockSize = transferBlockSize;
    }

    @Override
    public DataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DefaultDataDescriptor(storage, torrent, verifier, transferBlockSize);
    }
}
