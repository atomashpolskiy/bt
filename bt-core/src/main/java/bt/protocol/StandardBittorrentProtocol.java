package bt.protocol;

import bt.BtException;
import bt.Constants;
import com.google.inject.Inject;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static bt.protocol.Protocols.getInt;
import static bt.protocol.Protocols.getIntBytes;
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
        HANDSHAKE_PREFIX = new byte[1 + protocolNameLength + HANDSHAKE_RESERVED_LENGTH];
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
    public final Class<? extends Message> readMessageType(byte[] prefix) {

        Objects.requireNonNull(prefix);

        if (prefix.length == 0) {
            return null;

        } else if (prefix[0] == Constants.PROTOCOL_NAME.length()) {
            return Handshake.class;

        } else if (prefix.length >= MESSAGE_LENGTH_PREFIX_SIZE) {
            int length = getInt(prefix, 0);
            if (length == 0) {
                return KeepAlive.class;
            }

            if (prefix.length >= MESSAGE_PREFIX_SIZE) {

                int messageTypeId = prefix[MESSAGE_LENGTH_PREFIX_SIZE];
                Class<? extends Message> messageType;

                messageType = uniqueTypes.get(messageTypeId);
                if (messageType == null) {
                    MessageHandler<?> handler = handlers.get(messageTypeId);
                    if (handler == null) {
                        throw new InvalidMessageException("Unknown message type ID: " + messageTypeId);
                    }
                    messageType = handler.readMessageType(Arrays.copyOfRange(prefix, MESSAGE_PREFIX_SIZE, prefix.length));
                }
                return messageType;
            }
        }

        return null;
    }

    @Override
    public final int fromByteArray(MessageContext context, byte[] data) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(data);

        if (data.length == 0) {
            return 0;
        }

        Class<? extends Message> messageType = readMessageType(data);
        if (messageType == null) {
            return 0;
        }

        if (Handshake.class.equals(messageType)) {
            return decodeHandshake(context, data);
        }
        if (KeepAlive.class.equals(messageType)) {
            context.setMessage(KeepAlive.instance());
            return KEEPALIVE.length;
        }

        MessageHandler<?> handler = Objects.requireNonNull(handlersByType.get(messageType));
        int consumed = handler.decodePayload(context, Arrays.copyOfRange(data, MESSAGE_PREFIX_SIZE, data.length),
                getInt(data, 0) - MESSAGE_TYPE_SIZE);

        if (context.getMessage() != null) {
            return consumed + MESSAGE_PREFIX_SIZE;
        }
        return 0;
    }

    @Override
    public final byte[] toByteArray(Message message) {

        Objects.requireNonNull(message);

        if (Handshake.class.equals(message.getClass())) {
            Handshake handshake = (Handshake) message;
            return handshake(handshake.getInfoHash(), handshake.getPeerId());
        }
        if (KeepAlive.class.equals(message.getClass())) {
            return keepAlive();
        }

        Integer messageId = idMap.get(message.getClass());
        if (messageId == null) {
            throw new InvalidMessageException("Unknown message type: " + message.getClass().getSimpleName());
        }

        byte[] payload = doEncode(message, messageId);

        byte[] bytes = new byte[MESSAGE_PREFIX_SIZE + payload.length];
        System.arraycopy(getIntBytes(payload.length + MESSAGE_TYPE_SIZE), 0, bytes, 0, Integer.BYTES);
        bytes[MESSAGE_LENGTH_PREFIX_SIZE] = messageId.byteValue();
        System.arraycopy(payload, 0, bytes, MESSAGE_PREFIX_SIZE, payload.length);
        return bytes;
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> byte[] doEncode(T message, Integer messageId) {
        return ((MessageHandler<T>)handlers.get(messageId)).encodePayload(message);
    }

    // keep-alive: <len=0000>
    private static byte[] keepAlive() {
        return KEEPALIVE;
    }

    // handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
    private static byte[] handshake(byte[] infoHash, byte[] peerId) {

        if (infoHash.length != Constants.INFO_HASH_LENGTH) {
            throw new InvalidMessageException("Invalid info hash: expected " + Constants.INFO_HASH_LENGTH
                    + " bytes, received " + infoHash.length);
        }
        if (peerId.length != Constants.PEER_ID_LENGTH) {
            throw new InvalidMessageException("Invalid peer ID: expected " + Constants.PEER_ID_LENGTH
                    + " bytes, received " + peerId.length);
        }

        byte[] message = new byte[HANDSHAKE_PREFIX.length + Constants.INFO_HASH_LENGTH + Constants.PEER_ID_LENGTH];
        System.arraycopy(HANDSHAKE_PREFIX, 0, message, 0, HANDSHAKE_PREFIX.length);
        System.arraycopy(infoHash, 0, message, HANDSHAKE_PREFIX.length, infoHash.length);
        System.arraycopy(peerId, 0, message, HANDSHAKE_PREFIX.length + infoHash.length, peerId.length);

        return message;
    }

    private static int decodeHandshake(MessageContext context, byte[] data) {

        int consumed = 0;
        int offset = HANDSHAKE_RESERVED_OFFSET;
        int length = HANDSHAKE_RESERVED_LENGTH + Constants.INFO_HASH_LENGTH + Constants.PEER_ID_LENGTH;
        int limit = offset + length;

        if (data.length >= limit) {

            byte[] protocolNameBytes = Arrays.copyOfRange(data, 1, 1 + Constants.PROTOCOL_NAME.length());
            if (!Arrays.equals(PROTOCOL_NAME_BYTES, protocolNameBytes)) {
                throw new InvalidMessageException("Unexpected protocol name (decoded with ASCII): " +
                        new String(protocolNameBytes, Charset.forName("ASCII")));
            }

            int from, to;

            from = offset;
            to = offset + HANDSHAKE_RESERVED_LENGTH;
            byte[] reserved = Arrays.copyOfRange(data, from, to);

            from = to;
            to = to + Constants.INFO_HASH_LENGTH;
            byte[] infoHash = Arrays.copyOfRange(data, from, to);

            from = to;
            byte[] peerId = Arrays.copyOfRange(data, from, limit);

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
        public Class<? extends T> readMessageType(byte[] data) {
            return type;
        }
    }

    //--- fixed-size messages without payload ---//

    private static class ChokeHandler extends UniqueMessageHandler<Choke> {

        protected ChokeHandler() {
            super(Choke.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(Choke.class, 0, declaredPayloadLength);
            context.setMessage(Choke.instance());
            return 0;
        }

        @Override
        public byte[] encodePayload(Choke message) {
            return new byte[0];
        }
    }

    private static class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

        protected UnchokeHandler() {
            super(Unchoke.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(Unchoke.class, 0, declaredPayloadLength);
            context.setMessage(Unchoke.instance());
            return 0;
        }

        @Override
        public byte[] encodePayload(Unchoke message) {
            return new byte[0];
        }
    }

    private static class InterestedHandler extends UniqueMessageHandler<Interested> {

        protected InterestedHandler() {
            super(Interested.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(Interested.class, 0, declaredPayloadLength);
            context.setMessage(Interested.instance());
            return 0;
        }

        @Override
        public byte[] encodePayload(Interested message) {
            return new byte[0];
        }
    }

    private static class NotInterestedHandler extends UniqueMessageHandler<NotInterested> {

        protected NotInterestedHandler() {
            super(NotInterested.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(NotInterested.class, 0, declaredPayloadLength);
            context.setMessage(NotInterested.instance());
            return 0;
        }

        @Override
        public byte[] encodePayload(NotInterested message) {
            return new byte[0];
        }
    }

    //--- fixed-size messages with payload ---//

    private static class HaveHandler extends UniqueMessageHandler<Have> {

        protected HaveHandler() {
            super(Have.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(Have.class, 4, declaredPayloadLength);
            return decodeHave(context, data);
        }

        @Override
        public byte[] encodePayload(Have message) {
            return have(message.getPieceIndex());
        }

        // have: <len=0005><id=4><piece index>
        private static byte[] have(int pieceIndex) {
            if (pieceIndex < 0) {
                throw new InvalidMessageException("Invalid piece index: " + pieceIndex);
            }
            return getIntBytes(pieceIndex);
        }

        private static int decodeHave(MessageContext context, byte[] data) {

            int consumed = 0;
            int length = Integer.BYTES;

            if (data.length >= length) {
                int pieceIndex = getInt(data, 0);
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
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(Request.class, 12, declaredPayloadLength);
            return decodeRequest(context, data);
        }

        @Override
        public byte[] encodePayload(Request message) {
            return request(message.getPieceIndex(), message.getOffset(), message.getLength());
        }

        // request: <len=0013><id=6><index><begin><length>
        private static byte[] request(int pieceIndex, int offset, int length) {

            if (pieceIndex < 0 || offset < 0 || length <= 0) {
                throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                        + "), offset (" + offset + "), length (" + length + ")");
            }

            int messageLength = Integer.BYTES * 3;
            byte[] message = new byte[messageLength];
            System.arraycopy(getIntBytes(pieceIndex), 0, message, 0, Integer.BYTES);
            System.arraycopy(getIntBytes(offset), 0, message, Integer.BYTES, Integer.BYTES);
            System.arraycopy(getIntBytes(length), 0, message, Integer.BYTES * 2, Integer.BYTES);
            return message;
        }

        private static int decodeRequest(MessageContext context, byte[] data) {

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
    }

    private static class CancelHandler extends UniqueMessageHandler<Cancel> {

        protected CancelHandler() {
            super(Cancel.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            verifyPayloadLength(Cancel.class, 12, declaredPayloadLength);
            return decodeCancel(context, data);
        }

        @Override
        public byte[] encodePayload(Cancel message) {
            return cancel(message.getPieceIndex(), message.getOffset(), message.getLength());
        }

        // cancel: <len=0013><id=8><index><begin><length>
        private static byte[] cancel(int pieceIndex, int offset, int length) {

            if (pieceIndex < 0 || offset < 0 || length <= 0) {
                throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                        + "), offset (" + offset + "), length (" + length + ")");
            }

            int messageLength = Integer.BYTES * 3;
            byte[] message = new byte[messageLength];
            System.arraycopy(getIntBytes(pieceIndex), 0, message, 0, Integer.BYTES);
            System.arraycopy(getIntBytes(offset), 0, message, Integer.BYTES, Integer.BYTES);
            System.arraycopy(getIntBytes(length), 0, message, Integer.BYTES * 2, Integer.BYTES);
            return message;
        }

        private static int decodeCancel(MessageContext context, byte[] data) {

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
    }

    //--- variable-size messages with payload ---//

    private static class BitfieldHandler extends UniqueMessageHandler<Bitfield> {

        protected BitfieldHandler() {
            super(Bitfield.class);
        }

        @Override
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            return decodeBitfield(context, data, declaredPayloadLength);
        }

        @Override
        public byte[] encodePayload(Bitfield message) {
            return message.getBitfield();
        }

        // bitfield: <len=0001+X><id=5><bitfield>
        private static int decodeBitfield(MessageContext context, byte[] data, int length) {

            int consumed = 0;

            if (data.length >= length) {
                byte[] bitfield = data.length > length? Arrays.copyOfRange(data, 0, length) : data;
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
        public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
            return decodePiece(context, data, declaredPayloadLength);
        }

        @Override
        public byte[] encodePayload(Piece message) {
            return piece(message.getPieceIndex(), message.getOffset(), message.getBlock());
        }

        // piece: <len=0009+X><id=7><index><begin><block>
        private static byte[] piece(int pieceIndex, int offset, byte[] block) {

            if (pieceIndex < 0 || offset < 0) {
                throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                        + "), offset (" + offset + ")");
            }
            if (block.length == 0) {
                throw new InvalidMessageException("Invalid block: empty");
            }

            int messageLength = Integer.BYTES * 2 + block.length;
            byte[] message = new byte[messageLength];
            System.arraycopy(getIntBytes(pieceIndex), 0, message, 0, Integer.BYTES);
            System.arraycopy(getIntBytes(offset), 0, message, Integer.BYTES, Integer.BYTES);
            System.arraycopy(block, 0, message, Integer.BYTES * 2, block.length);
            return message;
        }

        private static int decodePiece(MessageContext context, byte[] data, int length) {

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
    }
}
