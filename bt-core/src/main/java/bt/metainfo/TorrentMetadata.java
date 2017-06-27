package bt.metainfo;

/**
 * @since 1.3
 */
public interface TorrentMetadata {

    /**
     * @return Torrent metadata in binary form
     * @since 1.3
     */
    byte[] getData();

    /**
     * @return Info dictionary in binary form
     * @since 1.3
     */
    byte[] getInfoDictionary();
}
