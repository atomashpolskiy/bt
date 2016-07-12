package bt.protocol.ext;

import bt.BtException;
import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.protocol.Message;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ExtendedHandshake implements Message {

    public static final String MESSAGE_TYPE_MAPPING_KEY = "m";

    public static Builder builder() {
        return new Builder();
    }

    private Map<String, BEObject<?>> data;

    ExtendedHandshake(Map<String, BEObject<?>> data) {
        this.data = Collections.unmodifiableMap(data);
    }

    public Map<String, BEObject<?>> getData() {
        return data;
    }

    public static class Builder {

        private Map<String, BEObject<?>> messageTypeMap;
        private Map<String, BEObject<?>> data;

        private Builder() {
            data = new HashMap<>();
        }

        public Builder addMessageType(String typeName, Integer typeId) {

            if (messageTypeMap == null) {
                messageTypeMap = new HashMap<>();
            }

            if (messageTypeMap.containsKey(Objects.requireNonNull(typeName))) {
                throw new BtException("Message type already defined: " + typeName);
            }

            messageTypeMap.put(typeName, new BEInteger(null, BigInteger.valueOf((long) typeId)));
            return this;
        }

        public ExtendedHandshake build() {

            if (messageTypeMap != null) {
                data.put(MESSAGE_TYPE_MAPPING_KEY, new BEMap(null, messageTypeMap));
            }
            return new ExtendedHandshake(data);
        }
    }
}
