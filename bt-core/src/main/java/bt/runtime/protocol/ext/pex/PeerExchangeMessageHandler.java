package bt.runtime.protocol.ext.pex;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.protocol.MessageContext;
import bt.protocol.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;

public class PeerExchangeMessageHandler implements MessageHandler<PeerExchange> {

    private final Collection<Class<? extends PeerExchange>> supportedTypes;

    public PeerExchangeMessageHandler() {
        supportedTypes = Collections.singleton(PeerExchange.class);
    }

    @Override
    public Collection<Class<? extends PeerExchange>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<PeerExchange> readMessageType(byte[] data) {
        return PeerExchange.class;
    }

    @Override
    public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {
        try (BEParser parser = new BEParser(data)) {
            BEMap messageContent = parser.readMap();
            PeerExchange message = PeerExchange.parse(messageContent);
            context.setMessage(message);
            return messageContent.getContent().length;
        }
    }

    @Override
    public byte[] encodePayload(PeerExchange message) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        message.writeTo(bos);
        return bos.toByteArray();
    }
}
