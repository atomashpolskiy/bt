package bt.net;

import bt.metainfo.Torrent;

public interface IConnectionHandlerFactory {

    ConnectionHandler getIncomingHandler();
    ConnectionHandler getOutgoingHandler(Torrent torrent);
}
