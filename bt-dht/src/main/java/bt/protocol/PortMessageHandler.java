package bt.protocol;

import bt.BtException;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static bt.protocol.Protocols.getShortBytes;
import static bt.protocol.Protocols.verifyPayloadLength;

public class PortMessageHandler implements MessageHandler<Port> {

    public static final int PORT_ID = 9;

    private static final int EXPECTED_PAYLOAD_LENGTH = Short.BYTES;

    private Collection<Class<? extends Port>> supportedTypes;

    public PortMessageHandler() {
        supportedTypes = Collections.singleton(Port.class);
    }

    @Override
    public Collection<Class<? extends Port>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends Port> readMessageType(ByteBuffer buffer) {
        return Port.class;
    }

    @Override
    public int decodePayload(MessageContext context, ByteBuffer buffer, int declaredPayloadLength) {
        verifyPayloadLength(Port.class, EXPECTED_PAYLOAD_LENGTH, declaredPayloadLength);
        return decodePort(context, buffer);
    }

    @Override
    public boolean encodePayload(Port message, ByteBuffer buffer) {
        return writePort(message.getPort(), buffer);
    }

    // port: <len=0003><id=9><listen-port>
    private static boolean writePort(int port, ByteBuffer buffer) {
        if (port < 0 || port > Short.MAX_VALUE * 2 + 1) {
            throw new BtException("Invalid port: " + port);
        }
        if (buffer.remaining() < Short.BYTES) {
            return false;
        }

        buffer.put(getShortBytes(port));
        return true;
    }

    private static int decodePort(MessageContext context, ByteBuffer buffer) throws InvalidMessageException {

        int consumed = 0;
        int length = Short.BYTES;

        if (buffer.remaining() >= length) {
            int port = Objects.requireNonNull(Protocols.readShort(buffer));
            context.setMessage(new Port(port));
            consumed = length;
        }

        return consumed;
    }
}
