package bt.torrent.data;

import bt.data.IDataDescriptor;

/**
 * Factory of data workers.
 *
 * @since 1.0
 */
public interface IDataWorkerFactory {

    /**
     * Create a data worker for a given torrent data descriptor.
     *
     * @since 1.0
     */
    IDataWorker createWorker(IDataDescriptor dataDescriptor);
}
