package bt.protocol;

import bt.net.Peer;
import bt.protocol.handler.MessageHandler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public abstract class ProtocolTest {

    protected final MessageHandler<Message> messageHandler;

    public ProtocolTest() {
        this.messageHandler = new StandardBittorrentProtocol(Collections.emptyMap());
    }

    public ProtocolTest(Map<Integer, MessageHandler<?>> extraHandlers) {
        this.messageHandler = new StandardBittorrentProtocol(Objects.requireNonNull(extraHandlers));
    }
    
    protected void assertInsufficientDataAndNothingConsumed(byte[] data) throws Exception {
        assertInsufficientDataAndNothingConsumed(null, data);
    }

    protected void assertInsufficientDataAndNothingConsumed(Class<? extends Message> expectedType,
                                                                byte[] data) throws Exception {

        ByteBuffer buffer = ByteBuffer.wrap(data).asReadOnlyBuffer();
        buffer.mark();

        byte[] copy = Arrays.copyOf(data, data.length);

        Class<? extends Message> actualType = messageHandler.readMessageType(buffer);
        buffer.reset();
        if (expectedType == null) {
            assertNull(actualType);
        } else {
            assertEquals(expectedType, actualType);
        }

        MessageContext context = createContext();
        int consumed = messageHandler.decode(context, buffer);
        buffer.reset();

        // check that buffer is not changed
        assertArrayEquals(copy, data);

        assertEquals(0, consumed);

        Message decoded = context.getMessage();
        assertNull(decoded);
    }

    protected void assertDecodedAndHasAttributes(Class<? extends Message> expectedType, int messageLength,
                                               Message expectedMessage, byte[] data) throws Exception {

        ByteBuffer in = ByteBuffer.wrap(data).asReadOnlyBuffer();
        in.mark();

        assertEquals(expectedType, messageHandler.readMessageType(in));
        in.reset();

        MessageContext context = createContext();
        int consumed = messageHandler.decode(context, in);
        in.reset();

        assertEquals(messageLength, consumed);

        Message decodedMessage = context.getMessage();
        assertMessageEquals(expectedMessage, decodedMessage);

        ByteBuffer out = ByteBuffer.allocate(messageLength);
        out.mark();
        assertTrue("Protocol failed to serialize message of length: " + messageLength,
                messageHandler.encode(decodedMessage, out));
        assertEquals(messageLength, out.position());

        byte[] encoded = new byte[messageLength];
        out.reset();
        out.get(encoded);

        if (data.length > messageLength) {
            data = Arrays.copyOfRange(data, 0, messageLength);
        }
        assertArrayEquals(data, encoded);
    }

    protected void assertMessageEquals(Message expected, Message actual) {

        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            throw new AssertionError("One of the messages is null and the other is not");
        }

        Class<? extends Message> expectedType = expected.getClass(), actualType = actual.getClass();
        assertEquals(expectedType, actualType);

        if (Handshake.class.equals(expectedType)) {
            Handshake expectedHandshake = (Handshake) expected, actualHandshake = (Handshake) actual;
            assertEquals(expectedHandshake.getTorrentId(), actualHandshake.getTorrentId());
            assertEquals(expectedHandshake.getPeerId(), actualHandshake.getPeerId());
            return;
        }
        if (KeepAlive.class.equals(expectedType)
                || Choke.class.equals(expectedType)
                || Unchoke.class.equals(expectedType)
                || Interested.class.equals(expectedType)
                || NotInterested.class.equals(expectedType)) {
            assertEquals(expected, actual);
            return;
        }
        if (Have.class.equals(expectedType)) {
            Have expectedHave = (Have) expected, actualHave = (Have) actual;
            assertEquals(expectedHave.getPieceIndex(), actualHave.getPieceIndex());
            return;
        }
        if (Bitfield.class.equals(expectedType)) {
            Bitfield expectedBitfield = (Bitfield) expected, actualBitfield = (Bitfield) actual;
            assertArrayEquals(expectedBitfield.getBitfield(), actualBitfield.getBitfield());
            return;
        }
        if (Request.class.equals(expectedType)) {
            Request expectedRequest = (Request) expected, actualRequest = (Request) actual;
            assertEquals(expectedRequest.getPieceIndex(), actualRequest.getPieceIndex());
            assertEquals(expectedRequest.getOffset(), actualRequest.getOffset());
            assertEquals(expectedRequest.getLength(), actualRequest.getLength());
            return;
        }
        if (Piece.class.equals(expectedType)) {
            Piece expectedPiece = (Piece) expected, actualPiece = (Piece) actual;
            assertEquals(expectedPiece.getPieceIndex(), actualPiece.getPieceIndex());
            assertEquals(expectedPiece.getOffset(), actualPiece.getOffset());
            assertArrayEquals(expectedPiece.getBlock(), actualPiece.getBlock());
            return;
        }
        if (Cancel.class.equals(expectedType)) {
            Cancel expectedCancel = (Cancel) expected, actualCancel = (Cancel) actual;
            assertEquals(expectedCancel.getPieceIndex(), actualCancel.getPieceIndex());
            assertEquals(expectedCancel.getOffset(), actualCancel.getOffset());
            assertEquals(expectedCancel.getLength(), actualCancel.getLength());
            return;
        }

        throw new AssertionError("Unexpected message type: " + expectedType.getSimpleName().toLowerCase());
    }

    protected static MessageContext createContext() {
        return new MessageContext(mock(Peer.class));
    }

    protected void testProtocol_InvalidLength(Class<? extends Message> type, int declaredLength, int expectedLength,
                                              byte[] data) throws Exception {

        ByteBuffer buffer = ByteBuffer.wrap(data).asReadOnlyBuffer();
        buffer.mark();

        int payloadLength = declaredLength - 1;

        String expectedMessage = "Unexpected payload length for " + type.getSimpleName() + ": " + payloadLength +
                " (expected " + expectedLength + ")";

        InvalidMessageException e = null;
        try {
            messageHandler.decode(createContext(), buffer);
        } catch (InvalidMessageException e1) {
            e = e1;
        }
        buffer.reset();

        assertNotNull(e);
        assertTrue("Expected message: {" + expectedMessage + "}, actual was: {" + e.getMessage() + "}",
                e.getMessage().startsWith(expectedMessage));
    }
}
