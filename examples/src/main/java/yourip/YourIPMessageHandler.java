package yourip;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.protocol.DecodingContext;
import bt.protocol.handler.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;

public class YourIPMessageHandler implements MessageHandler<YourIP> {

    @Override
    public boolean encode(YourIP message, ByteBuffer buffer) {
        boolean encoded = false;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            message.writeTo(bos);
            byte[] payload = bos.toByteArray();
            if (buffer.remaining() >= payload.length) {
                buffer.put(payload);
                encoded = true;
            }
        } catch (IOException e) {
            // can't happen
        }
        return encoded;
    }

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap message = parser.readMap();
            String address = new String(message.getValue().get(YourIP.addressField()).getContent(), Charset.forName("UTF-8"));
            context.setMessage(new YourIP(address));
            return message.getContent().length;
        }
    }

    @Override
    public Collection<Class<? extends YourIP>> getSupportedTypes() {
        return Collections.singleton(YourIP.class);
    }

    @Override
    public Class<? extends YourIP> readMessageType(ByteBuffer buffer) {
        return YourIP.class;
    }
}
