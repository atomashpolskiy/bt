package bt.data;

import bt.torrent.Bitfield;

import java.io.Closeable;
import java.util.List;

/**
 * Torrent's data descriptor.
 * Provides access to individual chunks and status of torrent's data.
 *
 * @since 1.0
 */
public interface IDataDescriptor extends Closeable {

    /**
     * @return List of chunks in the same order as they appear in torrent's metainfo.
     *         Hence, index of a chunk in this list can be used
     *         as the index of the corresponding piece in data exchange between peers.
     * @since 1.0
     */
    List<IChunkDescriptor> getChunkDescriptors();

    /**
     * @return Status of torrent's data.
     * @since 1.0
     */
    Bitfield getBitfield();
}
