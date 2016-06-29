package bt.protocol;

import bt.BtException;
import bt.Constants;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

import static bt.protocol.Protocols.getInt;

public abstract class BaseProtocol implements Protocol {

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

                byte messageTypeId = prefix[MESSAGE_LENGTH_PREFIX_SIZE];
                return getMessageType(messageTypeId);
            }
        }

        return null;
    }

    @Override
    public final int fromByteArray(Message[] messageHolder, byte[] data) {

        Objects.requireNonNull(messageHolder);
        Objects.requireNonNull(data);

        if (messageHolder.length == 0) {
            throw new BtException("Invalid message holder");
        }

        if (data.length == 0) {
            return -1;
        }

        Class<? extends Message> messageType = readMessageType(data);
        if (messageType == null) {
            return -1;
        }

        if (Handshake.class.equals(messageType)) {
            return decodeHandshake(messageHolder, data);
        }
        if (KeepAlive.class.equals(messageType)) {
            messageHolder[0] = KeepAlive.instance();
            return KEEPALIVE.length;
        }

        int consumed = fromByteArray(messageHolder, messageType,
                Arrays.copyOfRange(data, MESSAGE_PREFIX_SIZE, data.length),
                getInt(data, 0) - MESSAGE_TYPE_SIZE);

        if (consumed >= 0) {
            return consumed + MESSAGE_PREFIX_SIZE;
        }
        return -1;
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

        return doEncode(message);
    }

    protected abstract byte[] doEncode(Message message);

    // keep-alive: <len=0000>
    private static byte[] keepAlive() {
        return KEEPALIVE;
    }

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

        int consumed = -1;
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

            messageHolder[0] = new Handshake(reserved, infoHash, peerId);
            consumed = limit;
        }

        return consumed;
    }
}
