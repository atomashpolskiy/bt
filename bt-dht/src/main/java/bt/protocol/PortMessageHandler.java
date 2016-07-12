package bt.protocol;

import bt.BtException;

import java.util.Collection;
import java.util.Collections;

import static bt.protocol.Protocols.getShort;
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
    public Class<? extends Port> readMessageType(byte[] data) {
        return Port.class;
    }

    @Override
    public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
        verifyPayloadLength(Port.class, EXPECTED_PAYLOAD_LENGTH, declaredPayloadLength);
        return decodePort(context, data);
    }

    @Override
    public byte[] encodePayload(Port message) {
        return port(message.getPort());
    }

    // port: <len=0003><id=9><listen-port>
    private static byte[] port(int port) {
        if (port < 0 || port > Short.MAX_VALUE * 2 + 1) {
            throw new BtException("Invalid port: " + port);
        }
        return getShortBytes(port);
    }

    private static int decodePort(MessageContext context, byte[] data) throws InvalidMessageException {

        int consumed = 0;
        int length = Short.BYTES;

        if (data.length >= length) {
            int port = getShort(data, 0);
            context.setMessage(new Port(port));
            consumed = length;
        }

        return consumed;
    }
}
