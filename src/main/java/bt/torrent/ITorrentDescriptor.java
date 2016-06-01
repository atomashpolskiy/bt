package bt.torrent;

import bt.data.IDataDescriptor;
import bt.net.Peer;

public interface ITorrentDescriptor {

    boolean isActive();

    void start();

    void stop();

    void complete();

    Iterable<Peer> queryPeers();

    IDataDescriptor getDataDescriptor();
}
