package bt.protocol;

import bt.metainfo.TorrentId;
import bt.protocol.extended.ExtendedHandshake;

/**
 * @since 1.3
 */
public interface IExtendedHandshakeFactory {

    /**
     * @since 1.3
     */
    ExtendedHandshake getHandshake(TorrentId torrentId);
}
