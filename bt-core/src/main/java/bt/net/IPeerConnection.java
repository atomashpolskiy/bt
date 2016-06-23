package bt.net;

import bt.protocol.Message;

import java.io.Closeable;

public interface IPeerConnection extends Closeable {

    Object getTag();

    Message readMessageNow();

    Message readMessage(long timeout);

    void postMessage(Message message);

    Peer getRemotePeer();

    void closeQuietly();

    boolean isClosed();

    long getLastActive();
}
