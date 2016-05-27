package bt.protocol;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProtocolTestUtil {

    public static void assertInsufficientDataAndNothingConsumed(byte[] data) throws Exception {
        assertInsufficientDataAndNothingConsumed(null, data);
    }

    public static void assertInsufficientDataAndNothingConsumed(MessageType expectedType,
                                                                byte[] data) throws Exception {

        byte[] copy = Arrays.copyOf(data, data.length);

        MessageType actualType = Protocol.readMessageType(data);
        if (expectedType == null) {
            assertNull(actualType);
        } else {
            assertEquals(expectedType, actualType);
        }

        Message[] holder = new Message[1];
        int consumed = Protocol.fromByteArray(holder, data);

        // check that buffer is not changed
        assertArrayEquals(copy, data);

        assertEquals(0, consumed);

        Message decoded = holder[0];
        assertNull(decoded);
    }

    public static void assertDecodedAndHasAttributes(MessageType expectedType, int messageLength,
                                               Message expectedMessage, byte[] data) throws Exception {

        byte[] copy = Arrays.copyOf(data, data.length);

        assertEquals(expectedType, Protocol.readMessageType(data));

        Message[] holder = new Message[1];
        int consumed = Protocol.fromByteArray(holder, data);

        // check that buffer is not changed
        assertArrayEquals(copy, data);

        assertEquals(messageLength, consumed);

        Message decodedMessage = holder[0];
        assertMessageEquals(expectedMessage, decodedMessage);

        byte[] encoded = Protocol.toByteArray(decodedMessage);

        if (data.length > messageLength) {
            data = Arrays.copyOfRange(data, 0, messageLength);
        }
        assertArrayEquals(data, encoded);
    }

    public static void assertMessageEquals(Message expected, Message actual) {

        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            throw new AssertionError("One of the messages is null and the other is not");
        }

        MessageType expectedType = expected.getType(), actualType = actual.getType();
        assertEquals(expectedType, actualType);

        switch (expectedType) {
            case HANDSHAKE: {
                Handshake expectedHandshake = (Handshake) expected, actualHandshake = (Handshake) actual;
                assertArrayEquals(expectedHandshake.getInfoHash(), actualHandshake.getInfoHash());
                assertArrayEquals(expectedHandshake.getPeerId(), actualHandshake.getPeerId());
                break;
            }
            case KEEPALIVE:
            case CHOKE:
            case UNCHOKE:
            case INTERESTED:
            case NOT_INTERESTED: {
                assertEquals(expected, actual);
                break;
            }
            case HAVE: {
                Have expectedHave = (Have) expected, actualHave = (Have) actual;
                assertEquals(expectedHave.getPieceIndex(), actualHave.getPieceIndex());
                break;
            }
            case BITFIELD: {
                Bitfield expectedBitfield = (Bitfield) expected, actualBitfield = (Bitfield) actual;
                assertArrayEquals(expectedBitfield.getBitfield(), actualBitfield.getBitfield());
                break;
            }
            case REQUEST: {
                Request expectedRequest = (Request) expected, actualRequest = (Request) actual;
                assertEquals(expectedRequest.getPieceIndex(), actualRequest.getPieceIndex());
                assertEquals(expectedRequest.getOffset(), actualRequest.getOffset());
                assertEquals(expectedRequest.getLength(), actualRequest.getLength());
                break;
            }
            case PIECE: {
                Piece expectedPiece = (Piece) expected, actualPiece = (Piece) actual;
                assertEquals(expectedPiece.getPieceIndex(), actualPiece.getPieceIndex());
                assertEquals(expectedPiece.getOffset(), actualPiece.getOffset());
                assertArrayEquals(expectedPiece.getBlock(), actualPiece.getBlock());
                break;
            }
            case CANCEL: {
                Cancel expectedCancel = (Cancel) expected, actualCancel = (Cancel) actual;
                assertEquals(expectedCancel.getPieceIndex(), actualCancel.getPieceIndex());
                assertEquals(expectedCancel.getOffset(), actualCancel.getOffset());
                assertEquals(expectedCancel.getLength(), actualCancel.getLength());
                break;
            }
            case PORT: {
                Port expectedPort = (Port) expected, actualPort = (Port) actual;
                assertEquals(expectedPort.getPort(), actualPort.getPort());
                break;
            }
            default: {
                throw new AssertionError("Unexpected message type: " + expectedType.name().toLowerCase());
            }
        }
    }
}
