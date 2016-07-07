package bt.protocol;

import bt.BtException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static bt.protocol.Protocols.verifyPayloadLength;
import static bt.protocol.Protocols.getInt;
import static bt.protocol.Protocols.getIntBytes;

public class StandardBittorrentProtocol extends BaseProtocol {

    private static final int CHOKE_ID = 0;
    private static final int UNCHOKE_ID = 1;
    private static final int INTERESTED_ID = 2;
    private static final int NOT_INTERESTED_ID = 3;
    private static final int HAVE_ID = 4;
    private static final int BITFIELD_ID = 5;
    private static final int REQUEST_ID = 6;
    private static final int PIECE_ID = 7;
    private static final int CANCEL_ID = 8;

    private static final int[] supportedTypeIds = new int[] {
            CHOKE_ID, UNCHOKE_ID, INTERESTED_ID, NOT_INTERESTED_ID,
            HAVE_ID, BITFIELD_ID, REQUEST_ID, PIECE_ID, CANCEL_ID};

    //--- fixed-size messages without payload ---//
    private static final byte[] CHOKE = new byte[]{0,0,0,1,CHOKE_ID};
    private static final byte[] UNCHOKE = new byte[]{0,0,0,1,UNCHOKE_ID};
    private static final byte[] INTERESTED = new byte[]{0,0,0,1,INTERESTED_ID};
    private static final byte[] NOT_INTERESTED = new byte[]{0,0,0,1,NOT_INTERESTED_ID};

    //--- fixed-size messages with payload ---//
    private static final byte[] HAVE_PREFIX = new byte[]{0,0,0,5,HAVE_ID};
    private static final byte[] REQUEST_PREFIX = new byte[]{0,0,0,13,REQUEST_ID};
    private static final byte[] CANCEL_PREFIX = new byte[]{0,0,0,13,CANCEL_ID};

    private static final Set<Class<? extends Message>> supportedTypes;

    static {
        Set<Class<? extends Message>> _supportedTypes = new HashSet<>();
        _supportedTypes.add(Choke.class);
        _supportedTypes.add(Unchoke.class);
        _supportedTypes.add(Interested.class);
        _supportedTypes.add(NotInterested.class);
        _supportedTypes.add(Have.class);
        _supportedTypes.add(Bitfield.class);
        _supportedTypes.add(Request.class);
        _supportedTypes.add(Piece.class);
        _supportedTypes.add(Cancel.class);
        supportedTypes = Collections.unmodifiableSet(_supportedTypes);
    }

    @Override
    public boolean isSupported(Class<? extends Message> messageType) {
        return supportedTypes.contains(messageType);
    }

    @Override
    public boolean isSupported(byte messageTypeId) {

        for (int typeId : supportedTypeIds) {
            if (typeId == messageTypeId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Class<? extends Message>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends Message> getMessageType(byte messageTypeId) {

        switch (messageTypeId) {
            case CHOKE_ID: {
                return Choke.class;
            }
            case UNCHOKE_ID: {
                return Unchoke.class;
            }
            case INTERESTED_ID: {
                return Interested.class;
            }
            case NOT_INTERESTED_ID: {
                return NotInterested.class;
            }
            case HAVE_ID: {
                return Have.class;
            }
            case BITFIELD_ID: {
                return Bitfield.class;
            }
            case REQUEST_ID: {
                return Request.class;
            }
            case PIECE_ID: {
                return Piece.class;
            }
            case CANCEL_ID: {
                return Cancel.class;
            }
            default: {
                throw new InvalidMessageException("Unsupported message type ID: " + messageTypeId);
            }
        }
    }

    @Override
    public int fromByteArray(MessageContext context, Class<? extends Message> messageType,
                             byte[] data, int payloadLength) {

        assertSupported(Objects.requireNonNull(messageType));

        if (Choke.class.equals(messageType)) {
            verifyPayloadLength(messageType, 0, payloadLength);
            context.setMessage(Choke.instance());
            return 0;
        }
        if (Unchoke.class.equals(messageType)) {
            verifyPayloadLength(messageType, 0, payloadLength);
            context.setMessage(Unchoke.instance());
            return 0;
        }
        if (Interested.class.equals(messageType)) {
            verifyPayloadLength(messageType, 0, payloadLength);
            context.setMessage(Interested.instance());
            return 0;
        }
        if (NotInterested.class.equals(messageType)) {
            verifyPayloadLength(messageType, 0, payloadLength);
            context.setMessage(NotInterested.instance());
            return 0;
        }
        if (Have.class.equals(messageType)) {
            verifyPayloadLength(messageType, 4, payloadLength);
            return decodeHave(context, data);
        }
        if (Bitfield.class.equals(messageType)) {
            return decodeBitfield(context, data, payloadLength);
        }
        if (Request.class.equals(messageType)) {
            verifyPayloadLength(messageType, 12, payloadLength);
            return decodeRequest(context, data);
        }
        if (Piece.class.equals(messageType)) {
            return decodePiece(context, data, payloadLength);
        }
        if (Cancel.class.equals(messageType)) {
            verifyPayloadLength(messageType, 12, payloadLength);
            return decodeCancel(context, data);
        }

        // algorithm malfunction
        throw new BtException("Unexpected message type: " + messageType.getSimpleName());
    }

    @Override
    protected byte[] doEncode(Message message) {

        Class<? extends Message> messageType = message.getClass();
        assertSupported(messageType);

        if (Choke.class.equals(messageType)) {
            return choke();
        }
        if (Unchoke.class.equals(messageType)) {
            return unchoke();
        }
        if (Interested.class.equals(messageType)) {
            return interested();
        }
        if (NotInterested.class.equals(messageType)) {
            return notInterested();
        }
        if (Have.class.equals(messageType)) {
            Have have = (Have) message;
            return have(have.getPieceIndex());
        }
        if (Bitfield.class.equals(messageType)) {
            Bitfield bitfield = (Bitfield) message;
            return bitfield(bitfield.getBitfield());
        }
        if (Request.class.equals(messageType)) {
            Request request = (Request) message;
            return request(request.getPieceIndex(), request.getOffset(), request.getLength());
        }
        if (Piece.class.equals(messageType)) {
            Piece piece = (Piece) message;
            return piece(piece.getPieceIndex(), piece.getOffset(), piece.getBlock());
        }
        if (Cancel.class.equals(messageType)) {
            Cancel cancel = (Cancel) message;
            return cancel(cancel.getPieceIndex(), cancel.getOffset(), cancel.getLength());
        }

        // algorithm malfunction
        throw new BtException("Unexpected message type: " + messageType.getSimpleName());
    }

    //------------------------------------//
    //--- encoding / decoding messages ---//
    //------------------------------------//

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

    private static int decodeHave(MessageContext context, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int length = Integer.BYTES;

        if (data.length >= length) {
            int pieceIndex = getInt(data, 0);
            context.setMessage(new Have(pieceIndex));
            consumed = length;
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

    private static int decodeBitfield(MessageContext context, byte[] data, int length) {

        int consumed = 0;

        if (data.length >= length) {
            byte[] bitfield = Arrays.copyOfRange(data, 0, length);
            context.setMessage(new Bitfield(bitfield));
            consumed = length;
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

    private static int decodeRequest(MessageContext context, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (data.length >= length) {

            int pieceIndex = getInt(data, 0);
            int blockOffset = getInt(data, Integer.BYTES);
            int blockLength = getInt(data, Integer.BYTES * 2);

            context.setMessage(new Request(pieceIndex, blockOffset, blockLength));
            consumed = length;
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

    private static int decodePiece(MessageContext context, byte[] data, int length) throws InvalidMessageException {

        int consumed = 0;

        if (data.length >= length) {

            int pieceIndex = getInt(data, 0);
            int blockOffset = getInt(data, Integer.BYTES);
            byte[] block = Arrays.copyOfRange(data, Integer.BYTES * 2, length);

            context.setMessage(new Piece(pieceIndex, blockOffset, block));
            consumed = length;
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

    private static int decodeCancel(MessageContext context, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (data.length >= length) {

            int pieceIndex = getInt(data, 0);
            int blockOffset = getInt(data, Integer.BYTES);
            int blockLength = getInt(data, Integer.BYTES * 2);

            context.setMessage(new Cancel(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }

    private void assertSupported(Class<? extends Message> messageType) {
        if (!supportedTypes.contains(messageType)) {
            throw new InvalidMessageException("Unsupported message type: " + messageType.getSimpleName());
        }
    }
}
