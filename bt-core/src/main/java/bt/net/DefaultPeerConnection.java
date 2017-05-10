package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.DecodingContext;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @since 1.0
 */
class DefaultPeerConnection implements PeerConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPeerConnection.class);

    private static final long WAIT_BETWEEN_READS = 100L;

    private TorrentId torrentId;
    private Peer remotePeer;

    private ByteChannel channel;
    private PeerConnectionMessageReader messageReader;
    private PeerConnectionMessageWriter messageWriter;

    private volatile boolean closed;
    private AtomicLong lastActive;

    private final ReentrantLock readLock;
    private final Condition condition;

    DefaultPeerConnection(MessageHandler<Message> messageHandler,
                          Peer remotePeer,
                          ByteChannel channel,
                          long maxTransferBlockSize) {

        this.remotePeer = remotePeer;
        this.channel = channel;

        int bufferSize = getBufferSize(maxTransferBlockSize);
        this.messageReader = new PeerConnectionMessageReader(messageHandler, channel,
                () -> new DecodingContext(remotePeer), bufferSize);
        this.messageWriter = new PeerConnectionMessageWriter(messageHandler, channel, bufferSize);

        this.lastActive = new AtomicLong();

        this.readLock = new ReentrantLock(true);
        this.condition = this.readLock.newCondition();
    }

    private static int getBufferSize(long maxTransferBlockSize) {
        if (maxTransferBlockSize > ((Integer.MAX_VALUE - 13) / 2)) {
            throw new IllegalArgumentException("Transfer block size is too large: " + maxTransferBlockSize);
        }
        return (int) (maxTransferBlockSize) * 2;
    }

    /**
     * @since 1.0
     */
    void setTorrentId(TorrentId torrentId) {
        this.torrentId = torrentId;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    @Override
    public synchronized Message readMessageNow() {
        Message message = messageReader.readMessage();
        if (message != null) {
            updateLastActive();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Received message from peer: " + remotePeer + " -- " + message);
            }
        }
        return message;
    }

    @Override
    public synchronized Message readMessage(long timeout) {

        Message message = readMessageNow();
        if (message == null) {

            long started = System.currentTimeMillis();
            long remaining = timeout;

            // ... wait for the incoming message
            while (!closed) {
                try {
                    readLock.lock();
                    try {
                        condition.await(timeout < WAIT_BETWEEN_READS? timeout : WAIT_BETWEEN_READS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        // continue..
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
    public synchronized void postMessage(Message message) {
        updateLastActive();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Sending message to peer: " + remotePeer + " -- " + message);
        }
        messageWriter.writeMessage(message);
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
            try {
                channel.close();
            } finally {
                closed = true;
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public long getLastActive() {
        return lastActive.get();
    }
}
