package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Message;
import bt.protocol.MessageContext;
import bt.protocol.handler.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PeerConnection implements IPeerConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnection.class);

    private static final long WAIT_BETWEEN_READS = 100L;

    private TorrentId torrentId;
    private Peer remotePeer;

    private SocketChannel channel;
    private PeerConnectionMessageReader messageReader;
    private PeerConnectionMessageWriter messageWriter;

    private volatile boolean closed;
    private AtomicLong lastActive;

    private final ReentrantLock readLock;
    private final Condition condition;

    PeerConnection(MessageHandler<Message> messageHandler, Peer remotePeer,
                   SocketChannel channel, long maxTransferBlockSize) {

        this.remotePeer = remotePeer;
        this.channel = channel;

        int bufferSize = getBufferSize(maxTransferBlockSize);
        messageReader = new PeerConnectionMessageReader(messageHandler, channel,
                () -> new MessageContext(remotePeer), bufferSize);
        messageWriter = new PeerConnectionMessageWriter(messageHandler, channel, bufferSize);

        lastActive = new AtomicLong();

        readLock = new ReentrantLock(true);
        condition = readLock.newCondition();
    }

    private static int getBufferSize(long maxTransferBlockSize) {
        long bufferSize = maxTransferBlockSize * 2;
        bufferSize = Math.min(Integer.MAX_VALUE - 13, bufferSize);
        return (int) bufferSize;
    }

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
