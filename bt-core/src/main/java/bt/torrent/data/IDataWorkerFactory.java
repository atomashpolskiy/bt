package bt.torrent.data;

import bt.data.IDataDescriptor;

public interface IDataWorkerFactory {

    IDataWorker createWorker(IDataDescriptor dataDescriptor);
}
