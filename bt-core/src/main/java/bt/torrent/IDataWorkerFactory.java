package bt.torrent;

import bt.data.IDataDescriptor;

public interface IDataWorkerFactory {

    IDataWorker createWorker(IDataDescriptor dataDescriptor);
}
