package bt.metainfo;

import java.net.URL;

public interface IMetadataService {

    /**
     * Builds a torrent object from its binary representation located at {@code url}.
     * @param url binary bencoded representation of a torrent object
     * @return Torrent object.
     */
    Torrent fromUrl(URL url);

    /**
     * Builds a torrent object from its binary representation.
     * @param bs binary bencoded representation of a torrent object
     * @return Torrent object.
     */
    Torrent fromByteArray(byte[] bs);
}
