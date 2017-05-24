package bt.torrent.data;

import bt.data.ChunkVerifier;
import bt.data.DataDescriptor;
import bt.service.IRuntimeLifecycleBinder;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class DataWorkerFactory implements IDataWorkerFactory {

    private IRuntimeLifecycleBinder lifecycleBinder;
    private ChunkVerifier verifier;
    private int maxIOQueueSize;

    public DataWorkerFactory(IRuntimeLifecycleBinder lifecycleBinder, ChunkVerifier verifier, int maxIOQueueSize) {
        this.lifecycleBinder = lifecycleBinder;
        this.verifier = verifier;
        this.maxIOQueueSize = maxIOQueueSize;
    }

    @Override
    public DataWorker createWorker(DataDescriptor dataDescriptor) {
        return new DefaultDataWorker(lifecycleBinder, dataDescriptor, verifier, maxIOQueueSize);
    }
}
