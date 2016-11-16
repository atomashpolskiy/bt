package bt.metainfo;

import java.io.InputStream;
import java.net.URL;

/**
 * Service for creating torrents from bencoded sources.
 *
 * @since 1.0
 */
public interface IMetadataService {

    /**
     * Builds a torrent object from its binary representation located at {@code url}.
     *
     * @param url Binary bencoded representation of a torrent object
     * @return Torrent object.
     * @since 1.0
     */
    Torrent fromUrl(URL url);

    /**
     * Builds a torrent object from its binary representation.
     *
     * @param in Input stream, containing a bencoded representation.
     *           Caller should close the stream after the method has returned.
     * @return Torrent object.
     * @since 1.0
     */
    Torrent fromInputStream(InputStream in);

    /**
     * Builds a torrent object from its binary representation.
     *
     * @param bs binary bencoded representation of a torrent object
     * @return Torrent object.
     * @since 1.0
     */
    Torrent fromByteArray(byte[] bs);
}
