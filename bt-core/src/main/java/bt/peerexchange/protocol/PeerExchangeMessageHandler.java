package bt.peerexchange.protocol;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.protocol.DecodingContext;
import bt.protocol.handler.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
    public Class<PeerExchange> readMessageType(ByteBuffer buffer) {
        return PeerExchange.class;
    }

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap messageContent = parser.readMap();
            PeerExchange message = PeerExchange.parse(messageContent);
            context.setMessage(message);
            return messageContent.getContent().length;
        }
    }

    @Override
    public boolean encode(PeerExchange message, ByteBuffer buffer) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        message.writeTo(bos);

        byte[] payload = bos.toByteArray();
        if (buffer.remaining() < payload.length) {
            return false;
        }

        buffer.put(payload);
        return true;
    }
}
