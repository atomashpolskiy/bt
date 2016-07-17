package bt.runtime.protocol.ext;

import bt.BtException;
import bt.bencoding.BEParser;
import bt.bencoding.BEType;
import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.net.Peer;
import bt.protocol.InvalidMessageException;
import bt.protocol.MessageContext;
import bt.protocol.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ExtendedHandshakeMessageHandler implements MessageHandler<ExtendedHandshake> {

    private Collection<Class<? extends ExtendedHandshake>> supportedTypes;
    private ConcurrentMap<Peer, Map<Integer, String>> peerTypeMappings;

    ExtendedHandshakeMessageHandler() {
        peerTypeMappings = new ConcurrentHashMap<>();
        supportedTypes = Collections.singleton(ExtendedHandshake.class);
    }

    @Override
    public Collection<Class<? extends ExtendedHandshake>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends ExtendedHandshake> readMessageType(byte[] data) {
        return ExtendedHandshake.class;
    }

    private void processTypeMapping(Peer peer, BEObject mappingObj) {

        if (mappingObj == null) {
            return;
        }

        // TODO: use BE generic validation
        if (mappingObj.getType() != BEType.MAP) {
            throw new InvalidMessageException("Extended message types mapping must be a dictionary." +
                    " Actual BE type: " + mappingObj.getType().name());
        }

        @SuppressWarnings("unchecked")
        Map<String, BEObject> mapping = (Map<String, BEObject>) mappingObj.getValue();
        if (mapping.size() > 0) {
            // subsequent handshake messages can be used to enable/disable extensions
            // without restarting the connection
            peerTypeMappings.put(peer, mergeMappings(peerTypeMappings.getOrDefault(peer, new HashMap<>()), mapping));
        }
    }

    private Map<Integer, String> mergeMappings(Map<Integer, String> existing, Map<String, BEObject> changes) {

        for (Map.Entry<String, BEObject> entry : changes.entrySet()) {
            String typeName = entry.getKey();
            Integer typeId = ((BEInteger) entry.getValue()).getValue().intValue();
            existing.put(typeId, typeName);
        }
        return existing;
    }



    @Override
    public int decodePayload(MessageContext context, byte[] data, int declaredPayloadLength) {

        try (BEParser parser = new BEParser(data)) {
            BEMap message = parser.readMap();

            Map<String, BEObject<?>> value = message.getValue();
            processTypeMapping(context.getPeer(), value.get(ExtendedHandshake.MESSAGE_TYPE_MAPPING_KEY));
            context.setMessage(new ExtendedHandshake(value));
            return message.getContent().length;
        } catch (Exception e) {
            // TODO: parser should be configurable to return null instead of throwing an exception;
            // otherwise need to treat exceptions differently depending on their type
            // (i.e. incorrect message vs incomplete message)
            throw new BtException("Failed to decode extended handshake", e);
        }
    }

    @Override
    public byte[] encodePayload(ExtendedHandshake message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new BEMap(null, message.getData()).writeTo(out);
        return out.toByteArray();
    }
}
