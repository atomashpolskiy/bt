/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    public TorrentId setTorrentId(TorrentId torrentId) {
        return delegate.setTorrentId(torrentId);
    }

    @Override
    public TorrentId getTorrentId() {
        return delegate.getTorrentId();
    }

    @Override
    public Message readMessageNow() throws IOException {
        throw new UnsupportedOperationException("Connection is write-only");
    }

    @Override
    public Message readMessage(long timeout) throws IOException {
        throw new UnsupportedOperationException("Connection is write-only");
    }

    @Override
    public void postMessage(Message message) throws IOException {
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
