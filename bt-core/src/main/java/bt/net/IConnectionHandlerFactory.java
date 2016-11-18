package bt.net;

import bt.metainfo.TorrentId;

/**
 * @since 1.0
 */
public interface IConnectionHandlerFactory {

    /**
     * @return Connection handler, that should be used
     *         for processing incoming connections.
     * @since 1.0
     */
    ConnectionHandler getIncomingHandler();

    /**
     * @return Connection handler, that should be used
     *         for processing outgoing connections for a given torrent ID.
     * @since 1.0
     */
    ConnectionHandler getOutgoingHandler(TorrentId torrentId);
}
