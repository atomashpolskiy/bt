package bt.data;

import bt.metainfo.Torrent;

/**
 * Factory of torrent data descriptors.
 *
 * @since 1.0
 */
public interface IDataDescriptorFactory {

    /**
     * Create a data descriptor for a given torrent
     * with the storage provided as the data back-end.
     *
     * It's up to implementations to decide,
     * whether storage units will be allocated eagerly
     * upon creation of data descriptor or delayed
     * until data access is requested.
     *
     * @return Data descriptor
     * @since 1.0
     */
    DataDescriptor createDescriptor(Torrent torrent, Storage storage);
}
