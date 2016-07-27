package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Message;

import java.io.Closeable;

public interface IPeerConnection extends Closeable {

    TorrentId getTorrentId();

    Message readMessageNow();

    Message readMessage(long timeout);

    void postMessage(Message message);

    Peer getRemotePeer();

    void closeQuietly();

    boolean isClosed();

    long getLastActive();
}
