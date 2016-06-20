package bt.torrent;

import bt.data.IDataDescriptor;
import bt.service.IConfigurationService;
import com.google.inject.Inject;

public class DataWorkerFactory implements IDataWorkerFactory {

    private IConfigurationService configurationService;

    @Inject
    public DataWorkerFactory(IConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @Override
    public IDataWorker createWorker(IDataDescriptor dataDescriptor) {
        return new DataWorker(dataDescriptor.getChunkDescriptors(), configurationService.getReadRequestQueueMaxLength());
    }
}
