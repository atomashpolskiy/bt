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
import bt.net.pipeline.ChannelHandler;
import bt.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @since 1.0
 */
public class SocketPeerConnection implements PeerConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPeerConnection.class);

    private static final long WAIT_BETWEEN_READS = 100L;

    private final AtomicReference<TorrentId> torrentId;
    private final Peer remotePeer;

    private final ChannelHandler handler;

    private final AtomicLong lastActive;

    private final ReentrantLock readLock;
    private final Condition condition;

    SocketPeerConnection(Peer remotePeer, ChannelHandler handler) {
        this.torrentId = new AtomicReference<>();
        this.remotePeer = remotePeer;
        this.handler = handler;
        this.lastActive = new AtomicLong();
        this.readLock = new ReentrantLock(true);
        this.condition = this.readLock.newCondition();
    }

    /**
     * @since 1.0
     */
    @Override
    public TorrentId setTorrentId(TorrentId torrentId) {
        return this.torrentId.getAndSet(torrentId);
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId.get();
    }

    @Override
    public synchronized Message readMessageNow() throws IOException {
        Message message = handler.receive();
        if (message != null) {
            updateLastActive();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received message from peer: " + remotePeer + " -- " + message);
            }
        }
        return message;
    }

    @Override
    public synchronized Message readMessage(long timeout) throws IOException {
        Message message = readMessageNow();
        if (message == null) {

            long started = System.currentTimeMillis();
            long remaining = timeout;

            // ... wait for the incoming message
            while (!handler.isClosed()) {
                try {
                    readLock.lock();
                    try {
                        condition.await(timeout < WAIT_BETWEEN_READS? timeout : WAIT_BETWEEN_READS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Unexpectedly interrupted", e);
                    }
                    remaining -= WAIT_BETWEEN_READS;
                    message = readMessageNow();
                    if (message != null) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Received message from peer: " + remotePeer + " -- " + message +
                                    " (in " + (System.currentTimeMillis() - started) + " ms)");
                        }
                        return message;
                    } else if (remaining <= 0) {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Failed to read message from peer: " + remotePeer +
                                    " (in " + (System.currentTimeMillis() - started) + " ms)");
                        }
                        return null;
                    }
                } finally {
                    readLock.unlock();
                }
            }
        }
        return message;
    }

    @Override
    public synchronized void postMessage(Message message) throws IOException {
        updateLastActive();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Sending message to peer: " + remotePeer + " -- " + message);
        }
        if (!handler.send(message)) {
            throw new RuntimeException("Failed to send message");
        }
    }

    private void updateLastActive() {
        lastActive.set(System.currentTimeMillis());
    }

    @Override
    public Peer getRemotePeer() {
        return remotePeer;
    }

    @Override
    public void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            LOGGER.warn("Failed to close connection for peer: " + remotePeer, e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!isClosed()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Closing connection for peer: " + remotePeer);
            }
            handler.close();
        }
    }

    @Override
    public boolean isClosed() {
        return handler.isClosed();
    }

    @Override
    public long getLastActive() {
        return lastActive.get();
    }
}
