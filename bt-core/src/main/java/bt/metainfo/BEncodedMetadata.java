package bt.metainfo;

import bt.bencoding.model.BEMap;

/**
 * @since 1.3
 */
class BEncodedMetadata implements TorrentMetadata {

    private final BEMap metadata;
    private final BEMap infoDictionary;

    BEncodedMetadata(BEMap metadata, BEMap infoDictionary) {
        this.metadata = metadata;
        this.infoDictionary = infoDictionary;
    }

    @Override
    public byte[] getData() {
        return metadata.getContent();
    }

    @Override
    public byte[] getInfoDictionary() {
        return infoDictionary.getContent();
    }
}
