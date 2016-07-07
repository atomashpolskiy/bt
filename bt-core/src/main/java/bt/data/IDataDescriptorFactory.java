package bt.data;

import bt.metainfo.Torrent;

public interface IDataDescriptorFactory {

    IDataDescriptor createDescriptor(Torrent torrent, DataAccessFactory dataAccessFactory);
}
