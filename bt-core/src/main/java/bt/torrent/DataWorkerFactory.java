package bt.torrent;

import bt.data.IDataDescriptor;
import bt.service.IConfigurationService;
import bt.service.IShutdownService;
import com.google.inject.Inject;

public class DataWorkerFactory implements IDataWorkerFactory {

    private IConfigurationService configurationService;
    private IShutdownService shutdownService;

    @Inject
    public DataWorkerFactory(IConfigurationService configurationService, IShutdownService shutdownService) {
        this.configurationService = configurationService;
        this.shutdownService = shutdownService;
    }

    @Override
    public IDataWorker createWorker(IDataDescriptor dataDescriptor) {
        return new DataWorker(shutdownService, dataDescriptor.getChunkDescriptors(),
                configurationService.getReadRequestQueueMaxLength());
    }
}
