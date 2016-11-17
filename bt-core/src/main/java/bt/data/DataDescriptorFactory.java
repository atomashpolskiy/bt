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
    public IDataDescriptor createDescriptor(Torrent torrent, Storage storage) {
        return new DataDescriptor(storage, configurationService, torrent);
    }
}
