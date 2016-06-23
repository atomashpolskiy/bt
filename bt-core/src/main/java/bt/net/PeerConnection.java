package bt.net;

import bt.BtException;
import bt.Constants;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageType;
import bt.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PeerConnection implements IPeerConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerConnection.class);

    private static final long WAIT_BETWEEN_READS = 100L;
    private static final int BUFFER_CAPACITY = Constants.MAX_BLOCK_SIZE * 2;

    private Object tag;
    private Peer remotePeer;

    private SocketChannel channel;
    private ByteBuffer in;

    private volatile boolean closed;
    private AtomicLong lastActive;

    private byte[] readBytes;
    private Message[] messageHolder;

    private final ReentrantLock readLock;
    private final Condition condition;

    PeerConnection(Peer remotePeer, SocketChannel channel) {

        this.remotePeer = remotePeer;
        this.channel = channel;

        in = ByteBuffer.allocateDirect(BUFFER_CAPACITY);

        lastActive = new AtomicLong();
        messageHolder = new Message[1];

        readLock = new ReentrantLock(true);
        condition = readLock.newCondition();
    }

    void setTag(Object tag) {
        this.tag = tag;
    }

    @Override
    public Object getTag() {
        return tag;
    }

    @Override
    public synchronized Message readMessageNow() {

        try {
            if (readBytes != null && readBytes.length > 0) {
                Message message = readFromBuffer();
                if (message != null) {
                    return message;
                }
            }

            int read = channel.read(in);
            if (read > 0) {

                updateLastActive();

                in.rewind();

                // first bytes arrived for this connection
                if (readBytes == null) {
                    readBytes = new byte[read];
                    in.get(readBytes);

                } else {
                    // preserve leftovers from the previous reads if there are any
                    // and append fresh data to the end of the buffer
                    int offset = readBytes.length;
                    byte[] bytes = readBytes;
                    readBytes = new byte[offset + read];
                    System.arraycopy(bytes, 0, readBytes, 0, bytes.length);
                    in.get(readBytes, offset, read);
                }

                in.clear();

                return readFromBuffer();
            }
        } catch (InvalidMessageException | IOException e) {
            throw new BtException("Unexpected error in connection for peer: " + remotePeer, e);
        } finally {
            // always nullify the message holder
            messageHolder[0] = null;
        }

        return null;
    }

    private Message readFromBuffer() throws InvalidMessageException {

        MessageType messageType = Protocol.readMessageType(readBytes);
        if (messageType == null) {
            // protocol failed to determine the message type
            // due to the insufficient data; exiting...
            return null;

        } else {
            int consumed = Protocol.fromByteArray(messageHolder, readBytes);
            if (consumed == 0) {
                // protocol failed to read the message fully
                // because some data hasn't arrived yet; exiting...
                return null;
            } else {
                // remove consumed bytes from the beginning of the buffer
                readBytes = Arrays.copyOfRange(readBytes, consumed, readBytes.length);
                // and return the message
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Received message from peer: " + remotePeer + " -- " + messageHolder[0]);
                }
                return messageHolder[0];
            }
        }
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
                        condition.await(WAIT_BETWEEN_READS, TimeUnit.MILLISECONDS);
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

        try {
            channel.write(ByteBuffer.wrap(Protocol.toByteArray(message)));
        } catch (IOException e) {
            throw new BtException("Unexpected error in connection for peer: " + remotePeer, e);
        } catch (InvalidMessageException e) {
            throw new BtException("Failed to serialize outgoing message for peer: " + remotePeer + " -- " + message, e);
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
