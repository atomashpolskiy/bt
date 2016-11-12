package bt.data;

import bt.torrent.Bitfield;

import java.io.Closeable;
import java.util.List;

public interface IDataDescriptor extends Closeable {

    List<IChunkDescriptor> getChunkDescriptors();

    Bitfield getBitfield();
}
