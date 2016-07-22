package bt.data;

import java.io.Closeable;
import java.util.List;

public interface IDataDescriptor extends Closeable {

    List<IChunkDescriptor> getChunkDescriptors();
}
