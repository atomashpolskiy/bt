package bt.data;

import java.util.List;

public interface IDataDescriptor {

    List<IChunkDescriptor> getChunkDescriptors();
    void close();
}
