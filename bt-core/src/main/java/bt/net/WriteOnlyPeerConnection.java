package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Message;

import java.io.IOException;

class WriteOnlyPeerConnection implements PeerConnection {

    private final PeerConnection delegate;

    WriteOnlyPeerConnection(PeerConnection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Peer getRemotePeer() {
        return delegate.getRemotePeer();
    }

    @Override
    public TorrentId getTorrentId() {
        return delegate.getTorrentId();
    }

    @Override
    public Message readMessageNow() {
        throw new UnsupportedOperationException("Connection is write-only");
    }

    @Override
    public Message readMessage(long timeout) {
        throw new UnsupportedOperationException("Connection is write-only");
    }

    @Override
    public void postMessage(Message message) {
        delegate.postMessage(message);
    }

    @Override
    public long getLastActive() {
        return delegate.getLastActive();
    }

    @Override
    public void closeQuietly() {
        delegate.closeQuietly();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
