package bt.torrent.data;

import bt.data.IDataDescriptor;
import bt.service.IRuntimeLifecycleBinder;

public class DataWorkerFactory implements IDataWorkerFactory {

    private IRuntimeLifecycleBinder lifecycleBinder;
    private int maxIOQueueSize;

    public DataWorkerFactory(IRuntimeLifecycleBinder lifecycleBinder, int maxIOQueueSize) {
        this.lifecycleBinder = lifecycleBinder;
        this.maxIOQueueSize = maxIOQueueSize;
    }

    @Override
    public IDataWorker createWorker(IDataDescriptor dataDescriptor) {
        return new DataWorker(lifecycleBinder, dataDescriptor, maxIOQueueSize);
    }
}
