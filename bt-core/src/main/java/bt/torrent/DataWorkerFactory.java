package bt.torrent;

import bt.data.IDataDescriptor;
import bt.service.IConfigurationService;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;

public class DataWorkerFactory implements IDataWorkerFactory {

    private IConfigurationService configurationService;
    private IRuntimeLifecycleBinder lifecycleBinder;

    @Inject
    public DataWorkerFactory(IConfigurationService configurationService, IRuntimeLifecycleBinder lifecycleBinder) {
        this.configurationService = configurationService;
        this.lifecycleBinder = lifecycleBinder;
    }

    @Override
    public IDataWorker createWorker(IDataDescriptor dataDescriptor) {
        return new DataWorker(lifecycleBinder, dataDescriptor, configurationService.getReadRequestQueueMaxLength());
    }
}
