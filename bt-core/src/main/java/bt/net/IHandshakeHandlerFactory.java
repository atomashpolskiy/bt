package bt.net;

import bt.metainfo.Torrent;

public interface IHandshakeHandlerFactory {

    HandshakeHandler getIncomingHandler();
    HandshakeHandler getOutgoingHandler(Torrent torrent);
}
