package bt.data;

import bt.metainfo.Torrent;
import bt.service.IConfigurationService;
import com.google.inject.Inject;

public class DataDescriptorFactory implements IDataDescriptorFactory {

    private IConfigurationService configurationService;

    @Inject
    public DataDescriptorFactory(IConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @Override
    public IDataDescriptor createDescriptor(Torrent torrent, DataAccessFactory dataAccessFactory) {
        return new DataDescriptor(dataAccessFactory, configurationService, torrent);
    }
}
