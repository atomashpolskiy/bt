package bt.protocol;

import bt.BtException;
import bt.Constants;
import com.google.inject.Inject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.Protocols.verifyPayloadLength;

public class StandardBittorrentProtocol implements Protocol {

    private static final int CHOKE_ID = 0;
    private static final int UNCHOKE_ID = 1;
    private static final int INTERESTED_ID = 2;
    private static final int NOT_INTERESTED_ID = 3;
    private static final int HAVE_ID = 4;
    private static final int BITFIELD_ID = 5;
    private static final int REQUEST_ID = 6;
    private static final int PIECE_ID = 7;
    private static final int CANCEL_ID = 8;

    private static final int MESSAGE_LENGTH_PREFIX_SIZE = 4;
    private static final int MESSAGE_TYPE_SIZE = 1;
    private static final int MESSAGE_PREFIX_SIZE = MESSAGE_LENGTH_PREFIX_SIZE + MESSAGE_TYPE_SIZE;

    private static final byte[] PROTOCOL_NAME_BYTES;
    private static final byte[] HANDSHAKE_PREFIX;
    private static final int HANDSHAKE_RESERVED_OFFSET;
    private static final int HANDSHAKE_RESERVED_LENGTH = 8;

    private static final byte[] KEEPALIVE = new byte[]{0,0,0,0};

    static {

        PROTOCOL_NAME_BYTES = Constants.PROTOCOL_NAME.getBytes(Charset.forName("ASCII"));
        int protocolNameLength = PROTOCOL_NAME_BYTES.length;
        int prefixLength = 1;

        HANDSHAKE_RESERVED_OFFSET = 1 + protocolNameLength;
        HANDSHAKE_PREFIX = new byte[HANDSHAKE_RESERVED_OFFSET];
        HANDSHAKE_PREFIX[0] = (byte) protocolNameLength;
        System.arraycopy(PROTOCOL_NAME_BYTES, 0, HANDSHAKE_PREFIX, prefixLength, protocolNameLength);
    }

    private Map<Integer, MessageHandler<?>> handlers;
    private Map<Integer, Class<? extends Message>> uniqueTypes;
    private Map<Class<? extends Message>, MessageHandler<?>> handlersByType;
    private Map<Class<? extends Message>, Integer> idMap;

    @Inject
    public StandardBittorrentProtocol(Map<Integer, MessageHandler<?>> extraHandlers) {

        Map<Integer, MessageHandler<?>> handlers = new HashMap<>();
        handlers.put(CHOKE_ID, new ChokeHandler());
        handlers.put(UNCHOKE_ID, new UnchokeHandler());
        handlers.put(INTERESTED_ID, new InterestedHandler());
        handlers.put(NOT_INTERESTED_ID, new NotInterestedHandler());
        handlers.put(HAVE_ID, new HaveHandler());
        handlers.put(BITFIELD_ID, new BitfieldHandler());
        handlers.put(REQUEST_ID, new RequestHandler());
        handlers.put(PIECE_ID, new PieceHandler());
        handlers.put(CANCEL_ID, new CancelHandler());

        extraHandlers.forEach((messageId, handler) -> {
            if (handlers.containsKey(messageId)) {
                throw new BtException("Duplicate handler for message ID: " + messageId);
            }
            handlers.put(messageId, handler);
        });

        Map<Class<? extends Message>, Integer> idMap = new HashMap<>();
        Map<Class<? extends Message>, MessageHandler<?>> handlersByType = new HashMap<>();
        Map<Integer, Class<? extends Message>> uniqueTypes = new HashMap<>();

        handlers.forEach((messageId, handler) -> {

            if (handler.getSupportedTypes().isEmpty()) {
                throw new BtException("No supported types declared in handler: " + handler.getClass().getName());
            } else {
                uniqueTypes.put(messageId, handler.getSupportedTypes().iterator().next());
            }

            handler.getSupportedTypes().forEach(messageType -> {
                    if (idMap.containsKey(messageType)) {
                        throw new BtException("Duplicate handler for message type: " + messageType.getSimpleName());
                    }
                    idMap.put(messageType, messageId);
                    handlersByType.put(messageType, handler);
                }
            );
        });

        this.handlers = handlers;
        this.idMap = idMap;
        this.handlersByType = handlersByType;
        this.uniqueTypes = uniqueTypes;
    }

    @Override
    public final Class<? extends Message> readMessageType(ByteBuffer buffer) {

        Objects.requireNonNull(buffer);

        if (!buffer.hasRemaining()) {
            return null;
        }

        buffer.mark();
        byte first = buffer.get();
        if (first == Constants.PROTOCOL_NAME.length()) {
            return Handshake.class;
        }
        buffer.reset();

        Integer length = readInt(buffer);
        if (length == null) {
            return null;
        } else if (length == 0) {
            return KeepAlive.class;
        }

        if (buffer.hasRemaining()) {
            int messageTypeId = buffer.get();
            Class<? extends Message> messageType;

            messageType = uniqueTypes.get(messageTypeId);
            if (messageType == null) {
                MessageHandler<?> handler = handlers.get(messageTypeId);
                if (handler == null) {
                    throw new InvalidMessageException("Unknown message type ID: " + messageTypeId);
                }
                messageType = handler.readMessageType(buffer);
            }
            return messageType;
        }

        return null;
    }

    @Override
    public final int fromByteArray(MessageContext context, ByteBuffer buffer) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(buffer);

        if (!buffer.hasRemaining()) {
            return 0;
        }

        buffer.mark();
        Class<? extends Message> messageType = readMessageType(buffer);
        if (messageType == null) {
            return 0;
        }

        if (Handshake.class.equals(messageType)) {
            buffer.reset();
            return decodeHandshake(context, buffer);
        }
        if (KeepAlive.class.equals(messageType)) {
            context.setMessage(KeepAlive.instance());
            return KEEPALIVE.length;
        }

        MessageHandler<?> handler = Objects.requireNonNull(handlersByType.get(messageType));
        buffer.reset();
        Integer length = Objects.requireNonNull(readInt(buffer));
        buffer.get(); // skip message ID
        int consumed = handler.decodePayload(context, buffer, length - MESSAGE_TYPE_SIZE);

        if (context.getMessage() != null) {
            return consumed + MESSAGE_PREFIX_SIZE;
        }
        return 0;
    }

    @Override
    public final boolean toByteArray(Message message, ByteBuffer buffer) {

        Objects.requireNonNull(message);

        if (Handshake.class.equals(message.getClass())) {
            Handshake handshake = (Handshake) message;
            return writeHandshake(buffer, handshake.getReserved(), handshake.getInfoHash(), handshake.getPeerId());
        }
        if (KeepAlive.class.equals(message.getClass())) {
            return writeKeepAlive(buffer);
        }

        Integer messageId = idMap.get(message.getClass());
        if (messageId == null) {
            throw new InvalidMessageException("Unknown message type: " + message.getClass().getSimpleName());
        }

        if (buffer.remaining() < MESSAGE_PREFIX_SIZE) {
            return false;
        }

        int begin = buffer.position();
        buffer.position(begin + MESSAGE_PREFIX_SIZE);
        if (doEncode(message, messageId, buffer)) {
            int end = buffer.position();
            int payloadLength = end - begin - MESSAGE_PREFIX_SIZE;
            if (payloadLength < 0) {
                throw new BtException("Unexpected payload length: " + payloadLength);
            }
            buffer.position(begin);
            buffer.putInt(payloadLength + MESSAGE_TYPE_SIZE);
            buffer.put(messageId.byteValue());
            buffer.position(end);
            return true;
        } else {
            buffer.position(begin);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> boolean doEncode(T message, Integer messageId, ByteBuffer buffer) {
        return ((MessageHandler<T>)handlers.get(messageId)).encodePayload(message, buffer);
    }

    // keep-alive: <len=0000>
    private static boolean writeKeepAlive(ByteBuffer buffer) {
        if (buffer.remaining() < KEEPALIVE.length) {
            return false;
        }
        buffer.put(KEEPALIVE);
        return true;
    }

    // handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
    private static boolean writeHandshake(ByteBuffer buffer, byte[] reserved, byte[] infoHash, byte[] peerId) {

        if (reserved.length != HANDSHAKE_RESERVED_LENGTH) {
            throw new InvalidMessageException("Invalid reserved bytes: expected " + HANDSHAKE_RESERVED_LENGTH
                    + " bytes, received " + reserved.length);
        }
        if (infoHash.length != Constants.INFO_HASH_LENGTH) {
            throw new InvalidMessageException("Invalid info hash: expected " + Constants.INFO_HASH_LENGTH
                    + " bytes, received " + infoHash.length);
        }
        if (peerId.length != Constants.PEER_ID_LENGTH) {
            throw new InvalidMessageException("Invalid peer ID: expected " + Constants.PEER_ID_LENGTH
                    + " bytes, received " + peerId.length);
        }

        int length = HANDSHAKE_PREFIX.length + HANDSHAKE_RESERVED_LENGTH +
                Constants.INFO_HASH_LENGTH + Constants.PEER_ID_LENGTH;
        if (buffer.remaining() < length) {
            return false;
        }

        buffer.put(HANDSHAKE_PREFIX);
        buffer.put(reserved);
        buffer.put(infoHash);
        buffer.put(peerId);

        return true;
    }

    private static int decodeHandshake(MessageContext context, ByteBuffer buffer) {

        int consumed = 0;
        int offset = HANDSHAKE_RESERVED_OFFSET;
        int length = HANDSHAKE_RESERVED_LENGTH + Constants.INFO_HASH_LENGTH + Constants.PEER_ID_LENGTH;
        int limit = offset + length;

        if (buffer.remaining() >= limit) {

            buffer.get(); // skip message ID

            byte[] protocolNameBytes = new byte[Constants.PROTOCOL_NAME.length()];
            buffer.get(protocolNameBytes);
            if (!Arrays.equals(PROTOCOL_NAME_BYTES, protocolNameBytes)) {
                throw new InvalidMessageException("Unexpected protocol name (decoded with ASCII): " +
                        new String(protocolNameBytes, Charset.forName("ASCII")));
            }

            byte[] reserved = new byte[HANDSHAKE_RESERVED_LENGTH];
            buffer.get(reserved);

            byte[] infoHash = new byte[Constants.INFO_HASH_LENGTH];
            buffer.get(infoHash);

            byte[] peerId = new byte[Constants.PEER_ID_LENGTH];
            buffer.get(peerId);

            context.setMessage(new Handshake(reserved, infoHash, peerId));
            consumed = limit;
        }

        return consumed;
    }

    private static abstract class UniqueMessageHandler<T extends Message> implements MessageHandler<T> {

        private Class<T> type;
        private Collection<Class<? extends T>> supportedTypes;

        protected UniqueMessageHandler(Class<T> type) {
            this.type = type;
            supportedTypes = Collections.singleton(type);
        }

        @Override
        public Collection<Class<? extends T>> getSupportedTypes() {
            return supportedTypes;
        }

        @Override
        public Class<? extends T> readMessageType(ByteBuffer buffer) {
            return type;
        }
    }

    //--- fixed-size messages without payload ---//

    private static class ChokeHandler extends UniqueMessageHandler<Choke> {

        protected ChokeHandler() {
            super(Choke.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(Choke.class, 0, declaredPayloadLength);
            context.setMessage(Choke.instance());
            return 0;
        }

        @Override
        public boolean encodePayload(Choke message, ByteBuffer buffer) {
            return true;
        }
    }

    private static class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

        protected UnchokeHandler() {
            super(Unchoke.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(Unchoke.class, 0, declaredPayloadLength);
            context.setMessage(Unchoke.instance());
            return 0;
        }

        @Override
        public boolean encodePayload(Unchoke message, ByteBuffer buffer) {
            return true;
        }
    }

    private static class InterestedHandler extends UniqueMessageHandler<Interested> {

        protected InterestedHandler() {
            super(Interested.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(Interested.class, 0, declaredPayloadLength);
            context.setMessage(Interested.instance());
            return 0;
        }

        @Override
        public boolean encodePayload(Interested message, ByteBuffer buffer) {
            return true;
        }
    }

    private static class NotInterestedHandler extends UniqueMessageHandler<NotInterested> {

        protected NotInterestedHandler() {
            super(NotInterested.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(NotInterested.class, 0, declaredPayloadLength);
            context.setMessage(NotInterested.instance());
            return 0;
        }

        @Override
        public boolean encodePayload(NotInterested message, ByteBuffer buffer) {
            return true;
        }
    }

    //--- fixed-size messages with payload ---//

    private static class HaveHandler extends UniqueMessageHandler<Have> {

        protected HaveHandler() {
            super(Have.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(Have.class, 4, declaredPayloadLength);
            return decodeHave(context, buffer);
        }

        @Override
        public boolean encodePayload(Have message, ByteBuffer buffer) {
            return writeHave(message.getPieceIndex(), buffer);
        }

        // have: <len=0005><id=4><piece index>
        private static boolean writeHave(int pieceIndex, ByteBuffer buffer) {
            if (pieceIndex < 0) {
                throw new InvalidMessageException("Invalid piece index: " + pieceIndex);
            }
            if (buffer.remaining() < Integer.BYTES) {
                return false;
            }

            buffer.putInt(pieceIndex);
            return true;
        }

        private static int decodeHave(MessageContext context, ByteBuffer buffer) {

            int consumed = 0;
            int length = Integer.BYTES;

            if (buffer.remaining() >= length) {
                Integer pieceIndex = Objects.requireNonNull(readInt(buffer));
                context.setMessage(new Have(pieceIndex));
                consumed = length;
            }

            return consumed;
        }
    }

    private static class RequestHandler extends UniqueMessageHandler<Request> {

        protected RequestHandler() {
            super(Request.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(Request.class, 12, declaredPayloadLength);
            return decodeRequest(context, buffer);
        }

        @Override
        public boolean encodePayload(Request message, ByteBuffer buffer) {
            return writeRequest(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
        }

        // request: <len=0013><id=6><index><begin><length>
        private static boolean writeRequest(int pieceIndex, int offset, int length, ByteBuffer buffer) {

            if (pieceIndex < 0 || offset < 0 || length <= 0) {
                throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                        + "), offset (" + offset + "), length (" + length + ")");
            }
            if (buffer.remaining() < Integer.BYTES * 3) {
                return false;
            }

            buffer.putInt(pieceIndex);
            buffer.putInt(offset);
            buffer.putInt(length);

            return true;
        }

        private static int decodeRequest(MessageContext context, ByteBuffer buffer) {

            int consumed = 0;
            int length = Integer.BYTES * 3;

            if (buffer.remaining() >= length) {

                int pieceIndex = Objects.requireNonNull(readInt(buffer));
                int blockOffset = Objects.requireNonNull(readInt(buffer));
                int blockLength = Objects.requireNonNull(readInt(buffer));

                context.setMessage(new Request(pieceIndex, blockOffset, blockLength));
                consumed = length;
            }

            return consumed;
        }
    }

    private static class CancelHandler extends UniqueMessageHandler<Cancel> {

        protected CancelHandler() {
            super(Cancel.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            verifyPayloadLength(Cancel.class, 12, declaredPayloadLength);
            return decodeCancel(context, buffer);
        }

        @Override
        public boolean encodePayload(Cancel message, ByteBuffer buffer) {
            return writeCancel(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
        }

        // cancel: <len=0013><id=8><index><begin><length>
        private static boolean writeCancel(int pieceIndex, int offset, int length, ByteBuffer buffer) {

            if (pieceIndex < 0 || offset < 0 || length <= 0) {
                throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                        + "), offset (" + offset + "), length (" + length + ")");
            }
            if (buffer.remaining() < Integer.BYTES * 3) {
                return false;
            }

            buffer.putInt(pieceIndex);
            buffer.putInt(offset);
            buffer.putInt(length);

            return true;
        }

        private static int decodeCancel(MessageContext context, ByteBuffer buffer) {

            int consumed = 0;
            int length = Integer.BYTES * 3;

            if (buffer.remaining() >= length) {

                int pieceIndex = Objects.requireNonNull(readInt(buffer));
                int blockOffset = Objects.requireNonNull(readInt(buffer));
                int blockLength = Objects.requireNonNull(readInt(buffer));

                context.setMessage(new Cancel(pieceIndex, blockOffset, blockLength));
                consumed = length;
            }

            return consumed;
        }
    }

    //--- variable-size messages with payload ---//

    private static class BitfieldHandler extends UniqueMessageHandler<Bitfield> {

        protected BitfieldHandler() {
            super(Bitfield.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            return decodeBitfield(context, buffer, declaredPayloadLength);
        }

        @Override
        public boolean encodePayload(Bitfield message, ByteBuffer buffer) {
            if (buffer.remaining() < message.getBitfield().length) {
                return false;
            }
            buffer.put(message.getBitfield());
            return true;
        }

        // bitfield: <len=0001+X><id=5><bitfield>
        private static int decodeBitfield(MessageContext context, ByteBuffer buffer, int length) {

            int consumed = 0;

            if (buffer.remaining() >= length) {
                byte[] bitfield = new byte[length];
                buffer.get(bitfield);
                context.setMessage(new Bitfield(bitfield));
                consumed = length;
            }

            return consumed;
        }
    }

    private static class PieceHandler extends UniqueMessageHandler<Piece> {

        protected PieceHandler() {
            super(Piece.class);
        }

        @Override
        public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
            return decodePiece(context, buffer, declaredPayloadLength);
        }

        @Override
        public boolean encodePayload(Piece message, ByteBuffer buffer) {
            return writePiece(message.getPieceIndex(), message.getOffset(), message.getBlock(), buffer);
        }

        // piece: <len=0009+X><id=7><index><begin><block>
        private static boolean writePiece(int pieceIndex, int offset, byte[] block, ByteBuffer buffer) {

            if (pieceIndex < 0 || offset < 0) {
                throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                        + "), offset (" + offset + ")");
            }
            if (block.length == 0) {
                throw new InvalidMessageException("Invalid block: empty");
            }
            if (buffer.remaining() < Integer.BYTES * 2 + block.length) {
                return false;
            }

            buffer.putInt(pieceIndex);
            buffer.putInt(offset);
            buffer.put(block);

            return true;
        }

        private static int decodePiece(MessageContext context, ByteBuffer buffer, int length) {

            int consumed = 0;

            if (buffer.remaining() >= length) {

                int pieceIndex = Objects.requireNonNull(readInt(buffer));
                int blockOffset = Objects.requireNonNull(readInt(buffer));
                byte[] block = new byte[length - Integer.BYTES * 2];
                buffer.get(block);

                context.setMessage(new Piece(pieceIndex, blockOffset, block));
                consumed = length;
            }

            return consumed;
        }
    }
}
