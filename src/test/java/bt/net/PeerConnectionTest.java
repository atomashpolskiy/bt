package bt.net;

import bt.protocol.Bitfield;
import bt.protocol.Handshake;
import bt.protocol.InvalidMessageException;
import bt.protocol.Message;
import bt.protocol.MessageType;
import bt.protocol.Protocol;
import bt.protocol.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class PeerConnectionTest {

    private ServerSocketChannel serverChannel;
    private Server server;
    private SocketChannel clientChannel;

    @Before
    public void setUp() throws IOException {

        serverChannel = SelectorProvider.provider().openServerSocketChannel();
        serverChannel.bind(new InetSocketAddress(0));
        server = new Server(serverChannel);
        new Thread(server).start();

        SocketAddress localAddress = serverChannel.getLocalAddress();
        clientChannel = SelectorProvider.provider().openSocketChannel();
        clientChannel.connect(localAddress);
    }

    @Test
    public void testConnection() throws InvalidMessageException, IOException {
        PeerConnection connection = new PeerConnection(mock(Peer.class), clientChannel);

        Message message;

        server.writeMessage(new Handshake(new byte[20], new byte[20]));
        message = connection.readMessageNow();
        assertNotNull(message);
        assertEquals(MessageType.HANDSHAKE, message.getType());

        server.writeMessage(new Bitfield(new byte[2 << 9]));
        message = connection.readMessageNow();
        assertNotNull(message);
        assertEquals(MessageType.BITFIELD, message.getType());
        assertEquals(2 << 9, ((Bitfield) message).getBitfield().length);

        server.writeMessage(new Request(1, 2, 3));
        message = connection.readMessageNow();
        assertNotNull(message);
        assertEquals(MessageType.REQUEST, message.getType());
        assertEquals(1, ((Request) message).getPieceIndex());
        assertEquals(2, ((Request) message).getOffset());
        assertEquals(3, ((Request) message).getLength());
    }

    @After
    public void tearDown() {
        try {
            clientChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            serverChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Server implements Runnable, Closeable {

        private ServerSocketChannel channel;
        private volatile SocketChannel clientSocket;

        Server(ServerSocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            try {
                clientSocket = channel.accept();
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O error", e);
            }
        }

        public void writeMessage(Message message) throws InvalidMessageException, IOException {
            ByteBuffer buffer = ByteBuffer.wrap(Protocol.toByteArray(message));
            clientSocket.write(buffer);
        }

        @Override
        public void close() throws IOException {
            if (clientSocket != null) {
                clientSocket.close();
            }
            channel.close();
        }
    }
}
