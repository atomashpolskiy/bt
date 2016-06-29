package bt.protocol;

import bt.metainfo.Torrent;

public interface IHandshakeFactory {

    Handshake createHandshake(Torrent torrent);
}
