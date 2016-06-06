package bt.torrent;

import bt.data.IDataDescriptor;

public interface ITorrentDescriptor {

    boolean isActive();

    void start();

    void stop();

    void complete();

    IDataDescriptor getDataDescriptor();
}
