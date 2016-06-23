package bt.protocol;

import bt.BtException;
import bt.Constants;

import java.nio.charset.Charset;
import java.util.Arrays;

public class Protocol {

    private static final int MESSAGE_LENGTH_PREFIX_SIZE = 4;
    private static final int MESSAGE_TYPE_SIZE = 1;
    private static final int MESSAGE_PREFIX_SIZE = MESSAGE_LENGTH_PREFIX_SIZE + MESSAGE_TYPE_SIZE;

    private static final int CHOKE_ID = 0;
    private static final int UNCHOKE_ID = 1;
    private static final int INTERESTED_ID = 2;
    private static final int NOT_INTERESTED_ID = 3;
    private static final int HAVE_ID = 4;
    private static final int BITFIELD_ID = 5;
    private static final int REQUEST_ID = 6;
    private static final int PIECE_ID = 7;
    private static final int CANCEL_ID = 8;
    private static final int PORT_ID = 9;

    private static final String PROTOCOL_NAME = "BitTorrent protocol";
    private static final byte[] PROTOCOL_NAME_BYTES;
    private static final byte[] HANDSHAKE_PREFIX;

    //--- fixed-size messages without payload ---//
    private static final byte[] KEEPALIVE = new byte[]{0,0,0,0};
    private static final byte[] CHOKE = new byte[]{0,0,0,1,CHOKE_ID};
    private static final byte[] UNCHOKE = new byte[]{0,0,0,1,UNCHOKE_ID};
    private static final byte[] INTERESTED = new byte[]{0,0,0,1,INTERESTED_ID};
    private static final byte[] NOT_INTERESTED = new byte[]{0,0,0,1,NOT_INTERESTED_ID};

    //--- fixed-size messages with payload ---//
    private static final byte[] HAVE_PREFIX = new byte[]{0,0,0,5,HAVE_ID};
    private static final byte[] REQUEST_PREFIX = new byte[]{0,0,0,13,REQUEST_ID};
    private static final byte[] CANCEL_PREFIX = new byte[]{0,0,0,13,CANCEL_ID};
    private static final byte[] PORT_PREFIX = new byte[]{0,0,0,3,PORT_ID};

    static {

        PROTOCOL_NAME_BYTES = PROTOCOL_NAME.getBytes(Charset.forName("ASCII"));
        int protocolNameLength = PROTOCOL_NAME_BYTES.length;
        int prefixLength = 1, reservedLength = 8;

        HANDSHAKE_PREFIX = new byte[1 + protocolNameLength + reservedLength];
        HANDSHAKE_PREFIX[0] = (byte) protocolNameLength;
        System.arraycopy(PROTOCOL_NAME_BYTES, 0, HANDSHAKE_PREFIX, prefixLength, protocolNameLength);
    }

    /**
     * Tries to decode message from the byte buffer. If decoding is successful, then the result is set
     * into the first position in {@code messageHolder}
     *
     * @param messageHolder Length must not be less than 1 (must be a non-empty array)
     * @param data Byte buffer of arbitrary length containing (a part of) the message
     * @return Number of bytes consumed (0 if the provided data is insufficient)
     * @throws InvalidMessageException if message is invalid
     */
    public static int fromByteArray(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        if (messageHolder.length == 0) {
            return 0;
        }

        int consumed = 0;
        MessageType type = readMessageType(data);
        if (type != null) {
            switch (type) {
                case HANDSHAKE: {
                    consumed = decodeHandshake(messageHolder, data);
                    break;
                }
                case KEEPALIVE: {
                    messageHolder[0] = KeepAlive.instance();
                    consumed = KEEPALIVE.length;
                    break;
                }
                case CHOKE: {
                    assertHasLength(MessageType.CHOKE, data, 1);
                    messageHolder[0] = Choke.instance();
                    consumed = CHOKE.length;
                    break;
                }
                case UNCHOKE: {
                    assertHasLength(MessageType.UNCHOKE, data, 1);
                    messageHolder[0] = Unchoke.instance();
                    consumed = UNCHOKE.length;
                    break;
                }
                case INTERESTED: {
                    assertHasLength(MessageType.INTERESTED, data, 1);
                    messageHolder[0] = Interested.instance();
                    consumed = INTERESTED.length;
                    break;
                }
                case NOT_INTERESTED: {
                    assertHasLength(MessageType.NOT_INTERESTED, data, 1);
                    messageHolder[0] = NotInterested.instance();
                    consumed = NOT_INTERESTED.length;
                    break;
                }
                case HAVE: {
                    assertHasLength(MessageType.HAVE, data, 5);
                    consumed = decodeHave(messageHolder, data);
                    break;
                }
                case BITFIELD: {
                    consumed = decodeBitfield(messageHolder, data);
                    break;
                }
                case REQUEST: {
                    assertHasLength(MessageType.REQUEST, data, 13);
                    consumed = decodeRequest(messageHolder, data);
                    break;
                }
                case PIECE: {
                    consumed = decodePiece(messageHolder, data);
                    break;
                }
                case CANCEL: {
                    assertHasLength(MessageType.CANCEL, data, 13);
                    consumed = decodeCancel(messageHolder, data);
                    break;
                }
                case PORT: {
                    assertHasLength(MessageType.PORT, data, 3);
                    consumed = decodePort(messageHolder, data);
                    break;
                }
                default: {
                    throw new InvalidMessageException("Unexpected message type: " + type.name().toLowerCase());
                }
            }
        }

        return consumed;
    }

    private static void assertHasLength(MessageType type, byte[] data, int expectedLength) throws InvalidMessageException {

        if (data == null) {
            // algorithm malfunction
            throw new NullPointerException("Failed to read length: data is null");
        }

        if (data.length < Integer.BYTES) {
            throw new InvalidMessageException("Failed to read length: insufficient bytes");
        }

        int declaredLength = getInt(data, 0);
        if (declaredLength != expectedLength) {
            throw new InvalidMessageException("Unexpected declared length for " + type.name() + ": " + declaredLength);
        }
    }


    /**
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid
     */
    public static MessageType readMessageType(byte[] prefix) throws InvalidMessageException {

        if (prefix.length == 0) {
            return null;

        } else {
            if (prefix[0] == PROTOCOL_NAME.length()) {
                return MessageType.HANDSHAKE;
            }

            if (prefix.length >= MESSAGE_LENGTH_PREFIX_SIZE) {

                int length = getInt(prefix, 0);
                if (length == 0) {
                    return MessageType.KEEPALIVE;
                }

                if (prefix.length >= MESSAGE_PREFIX_SIZE) {
                    switch (prefix[MESSAGE_LENGTH_PREFIX_SIZE]) {
                        case CHOKE_ID: {
                            return MessageType.CHOKE;
                        }
                        case UNCHOKE_ID: {
                            return MessageType.UNCHOKE;
                        }
                        case INTERESTED_ID: {
                            return MessageType.INTERESTED;
                        }
                        case NOT_INTERESTED_ID: {
                            return MessageType.NOT_INTERESTED;
                        }
                        case HAVE_ID: {
                            return MessageType.HAVE;
                        }
                        case BITFIELD_ID: {
                            return MessageType.BITFIELD;
                        }
                        case REQUEST_ID: {
                            return MessageType.REQUEST;
                        }
                        case PIECE_ID: {
                            return MessageType.PIECE;
                        }
                        case CANCEL_ID: {
                            return MessageType.CANCEL;
                        }
                        case PORT_ID: {
                            return MessageType.PORT;
                        }
                        default: {
                            throw new InvalidMessageException(
                                    "Invalid message prefix (" + MESSAGE_PREFIX_SIZE + " first bytes): " +
                                            Arrays.toString(Arrays.copyOfRange(prefix, 0, MESSAGE_PREFIX_SIZE)));
                        }
                    }
                }
            }
        }
        return null;
    }

    public static byte[] toByteArray(Message message) throws InvalidMessageException {

        if (message == null) {
            throw new NullPointerException("Message is null");
        }

        byte[] bytes;

        MessageType type = message.getType();
        switch (type) {
            case HANDSHAKE: {
                Handshake handshake = (Handshake) message;
                bytes = handshake(handshake.getInfoHash(), handshake.getPeerId());
                break;
            }
            case KEEPALIVE: {
                bytes = keepAlive();
                break;
            }
            case CHOKE: {
                bytes = choke();
                break;
            }
            case UNCHOKE: {
                bytes = unchoke();
                break;
            }
            case INTERESTED: {
                bytes = interested();
                break;
            }
            case NOT_INTERESTED: {
                bytes = notInterested();
                break;
            }
            case HAVE: {
                Have have = (Have) message;
                bytes = have(have.getPieceIndex());
                break;
            }
            case BITFIELD: {
                Bitfield bitfield = (Bitfield) message;
                bytes = bitfield(bitfield.getBitfield());
                break;
            }
            case REQUEST: {
                Request request = (Request) message;
                bytes = request(request.getPieceIndex(), request.getOffset(), request.getLength());
                break;
            }
            case PIECE: {
                Piece piece = (Piece) message;
                bytes = piece(piece.getPieceIndex(), piece.getOffset(), piece.getBlock());
                break;
            }
            case CANCEL: {
                Cancel cancel = (Cancel) message;
                bytes = cancel(cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength());
                break;
            }
            case PORT: {
                Port port = (Port) message;
                bytes = port(port.getPort());
                break;
            }
            default: {
                throw new InvalidMessageException("Unexpected message type: " + type.name().toLowerCase());
            }
        }

        return bytes;
    }

    //------------------------------------//
    //--- encoding / decoding messages ---//
    //------------------------------------//

    // handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
    private static byte[] handshake(byte[] infoHash, byte[] peerId) {

        if (infoHash.length != Constants.INFO_HASH_LENGTH) {
            throw new BtException("Invalid info hash: expected " + Constants.INFO_HASH_LENGTH
                    + " bytes, received " + infoHash.length);
        }
        if (peerId.length != Constants.PEER_ID_LENGTH) {
            throw new BtException("Invalid peer ID: expected " + Constants.PEER_ID_LENGTH
                    + " bytes, received " + peerId.length);
        }

        byte[] message = new byte[HANDSHAKE_PREFIX.length + Constants.INFO_HASH_LENGTH + Constants.PEER_ID_LENGTH];
        System.arraycopy(HANDSHAKE_PREFIX, 0, message, 0, HANDSHAKE_PREFIX.length);
        System.arraycopy(infoHash, 0, message, HANDSHAKE_PREFIX.length, infoHash.length);
        System.arraycopy(peerId, 0, message, HANDSHAKE_PREFIX.length + infoHash.length, peerId.length);

        return message;
    }

    private static int decodeHandshake(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int offset = HANDSHAKE_PREFIX.length;
        int length = Constants.INFO_HASH_LENGTH + Constants.PEER_ID_LENGTH;
        int limit = offset + length;

        if (data.length >= limit) {

            byte[] protocolNameBytes = Arrays.copyOfRange(data, 1, 1 + PROTOCOL_NAME.length());
            if (!Arrays.equals(PROTOCOL_NAME_BYTES, protocolNameBytes)) {
                throw new InvalidMessageException("Unexpected protocol name (decoded with ASCII): " +
                        new String(protocolNameBytes, Charset.forName("ASCII")));
            }

            byte[] infoHash = Arrays.copyOfRange(data, offset, offset + Constants.INFO_HASH_LENGTH);
            byte[] peerId = Arrays.copyOfRange(data, offset + Constants.INFO_HASH_LENGTH, limit);

            messageHolder[0] = new Handshake(infoHash, peerId);
            consumed = limit;
        }

        return consumed;
    }

    // keep-alive: <len=0000>
    private static byte[] keepAlive() {
        return KEEPALIVE;
    }

    // choke: <len=0001><id=0>
    private static byte[] choke() {
        return CHOKE;
    }

    // unchoke: <len=0001><id=1>
    private static byte[] unchoke() {
        return UNCHOKE;
    }

    // interested: <len=0001><id=2>
    private static byte[] interested() {
        return INTERESTED;
    }

    // not interested: <len=0001><id=3>
    private static byte[] notInterested() {
        return NOT_INTERESTED;
    }

    // have: <len=0005><id=4><piece index>
    private static byte[] have(int pieceIndex) {

        if (pieceIndex < 0) {
            throw new BtException("Invalid piece index: " + pieceIndex);
        }

        byte[] message = new byte[HAVE_PREFIX.length + Integer.BYTES];
        System.arraycopy(HAVE_PREFIX, 0, message, 0, HAVE_PREFIX.length);
        System.arraycopy(getIntBytes(pieceIndex), 0, message, HAVE_PREFIX.length, Integer.BYTES);
        return message;
    }

    private static int decodeHave(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int offset = HAVE_PREFIX.length;
        int length = Integer.BYTES;

        if (data.length >= offset + length) {
            int pieceIndex = getInt(data, offset);
            messageHolder[0] = new Have(pieceIndex);
            consumed = offset + length;
        }

        return consumed;
    }

    // bitfield: <len=0001+X><id=5><bitfield>
    private static byte[] bitfield(byte[] bitfield) {

        int messageLength = Integer.BYTES + 1 + bitfield.length;
        byte[] message = new byte[messageLength];
        System.arraycopy(getIntBytes(messageLength - Integer.BYTES), 0, message, 0, Integer.BYTES);
        message[Integer.BYTES] = BITFIELD_ID;
        System.arraycopy(bitfield, 0, message, Integer.BYTES + 1, bitfield.length);
        return message;
    }

    private static int decodeBitfield(Message[] messageHolder, byte[] data) {

        int consumed = 0;
        int offset = MESSAGE_PREFIX_SIZE;
        int length = getInt(data, 0);
        int limit = offset + length - MESSAGE_TYPE_SIZE;

        if (data.length >= limit) {
            byte[] bitfield = Arrays.copyOfRange(data, offset, limit);
            messageHolder[0] = new Bitfield(bitfield);
            consumed = limit;
        }

        return consumed;
    }

    // request: <len=0013><id=6><index><begin><length>
    private static byte[] request(int pieceIndex, int offset, int length) {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new BtException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")");
        }

        int messageLength = REQUEST_PREFIX.length + Integer.BYTES * 3;
        byte[] message = new byte[messageLength];
        System.arraycopy(REQUEST_PREFIX, 0, message, 0, REQUEST_PREFIX.length);
        System.arraycopy(getIntBytes(pieceIndex), 0, message, REQUEST_PREFIX.length, Integer.BYTES);
        System.arraycopy(getIntBytes(offset), 0, message, REQUEST_PREFIX.length + Integer.BYTES, Integer.BYTES);
        System.arraycopy(getIntBytes(length), 0, message, REQUEST_PREFIX.length + Integer.BYTES * 2, Integer.BYTES);
        return message;
    }

    private static int decodeRequest(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int offset = REQUEST_PREFIX.length;
        int length = Integer.BYTES * 3;

        if (data.length >= offset + length) {

            int pieceIndex = getInt(data, offset);
            int blockOffset = getInt(data, offset + Integer.BYTES);
            int blockLength = getInt(data, offset + Integer.BYTES * 2);

            messageHolder[0] = new Request(pieceIndex, blockOffset, blockLength);
            consumed = offset + length;
        }

        return consumed;
    }

    // piece: <len=0009+X><id=7><index><begin><block>
    private static byte[] piece(int pieceIndex, int offset, byte[] block) {

        if (pieceIndex < 0 || offset < 0) {
            throw new BtException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + ")");
        }
        if (block.length == 0) {
            throw new BtException("Invalid block: empty");
        }

        int messageLength = Integer.BYTES + 1 + Integer.BYTES * 2 + block.length;
        byte[] message = new byte[messageLength];
        System.arraycopy(getIntBytes(messageLength - Integer.BYTES), 0, message, 0, Integer.BYTES);
        message[Integer.BYTES] = PIECE_ID;
        System.arraycopy(getIntBytes(pieceIndex), 0, message, Integer.BYTES + 1, Integer.BYTES);
        System.arraycopy(getIntBytes(offset), 0, message, Integer.BYTES * 2 + 1, Integer.BYTES);
        System.arraycopy(block, 0, message, Integer.BYTES * 3 + 1, block.length);
        return message;
    }

    private static int decodePiece(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int offset = MESSAGE_PREFIX_SIZE;
        int length = getInt(data, 0);
        int limit = offset + length - MESSAGE_TYPE_SIZE;

        if (data.length >= limit) {

            int pieceIndex = getInt(data, offset);
            int blockOffset = getInt(data, offset + Integer.BYTES);
            byte[] block = Arrays.copyOfRange(data, offset + Integer.BYTES * 2, limit);

            messageHolder[0] = new Piece(pieceIndex, blockOffset, block);
            consumed = limit;
        }

        return consumed;
    }

    // cancel: <len=0013><id=8><index><begin><length>
    private static byte[] cancel(int pieceIndex, int offset, int length) {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new BtException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")");
        }

        int messageLength = CANCEL_PREFIX.length + Integer.BYTES * 3;
        byte[] message = new byte[messageLength];
        System.arraycopy(CANCEL_PREFIX, 0, message, 0, CANCEL_PREFIX.length);
        System.arraycopy(getIntBytes(pieceIndex), 0, message, CANCEL_PREFIX.length, Integer.BYTES);
        System.arraycopy(getIntBytes(offset), 0, message, CANCEL_PREFIX.length + Integer.BYTES, Integer.BYTES);
        System.arraycopy(getIntBytes(length), 0, message, CANCEL_PREFIX.length + Integer.BYTES * 2, Integer.BYTES);
        return message;
    }

    private static int decodeCancel(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int offset = CANCEL_PREFIX.length;
        int length = Integer.BYTES * 3;

        if (data.length >= offset + length) {

            int pieceIndex = getInt(data, offset);
            int blockOffset = getInt(data, offset + Integer.BYTES);
            int blockLength = getInt(data, offset + Integer.BYTES * 2);

            messageHolder[0] = new Cancel(pieceIndex, blockOffset, blockLength);
            consumed = offset + length;
        }

        return consumed;
    }

    // port: <len=0003><id=9><listen-port>
    private static byte[] port(int port) {

        if (port < 0 || port > Short.MAX_VALUE * 2 + 1) {
            throw new BtException("Invalid port: " + port);
        }

        int messageLength = PORT_PREFIX.length + Short.BYTES;
        byte[] message = new byte[messageLength];
        System.arraycopy(PORT_PREFIX, 0, message, 0, PORT_PREFIX.length);
        System.arraycopy(getShortBytes(port), 0, message, PORT_PREFIX.length, Short.BYTES);
        return message;
    }

    private static int decodePort(Message[] messageHolder, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int offset = PORT_PREFIX.length;
        int length = Short.BYTES;

        if (data.length >= offset + length) {

            int port = getShort(data, offset);
            messageHolder[0] = new Port(port);
            consumed = offset + length;
        }

        return consumed;
    }


    //-------------------------//
    //--- utility functions ---//
    //-------------------------//

    private static byte[] getIntBytes(int i) {
        return new byte[] {(byte) (i >> 24), (byte) (i >> 16), (byte) (i >> 8), (byte) i};
    }

    private static byte[] getShortBytes(int s) {
        return new byte[] {(byte) (s >> 8), (byte) s};
    }

    /**
     * {@code bytes.length} must be at least {@code offset + java.lang.Integer.BYTES}
     */
    private static int getInt(byte[] bytes, int offset) {

        if (bytes.length < offset + Integer.BYTES) {
            throw new ArrayIndexOutOfBoundsException("insufficient byte array length (length: " + bytes.length +
                    ", offset: " + offset + ")");
        }
        // intentionally do not check bytes.length,
        // just take the first 4 bytes (starting with the offset)
        return ((bytes[offset] << 24) & 0xFF000000) + ((bytes[offset + 1] << 16) & 0x00FF0000)
                + ((bytes[offset + 2] << 8) & 0x0000FF00) + (bytes[offset + 3] & 0x000000FF);
    }

    /**
     * {@code bytes.length} must be at least {@code offset + java.lang.Short.BYTES}
     */
    private static int getShort(byte[] bytes, int offset) {

        if (bytes.length < offset + Short.BYTES) {
            throw new ArrayIndexOutOfBoundsException("insufficient byte array length (length: " + bytes.length +
                    ", offset: " + offset + ")");
        }
        // intentionally do not check bytes.length,
        // just take the first 2 bytes (starting with the offset)
        return ((bytes[offset] << 8) & 0xFF00) + (bytes[offset + 1] & 0x00FF);
    }
}
