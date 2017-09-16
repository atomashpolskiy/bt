/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.protocol.extended;

import bt.BtException;
import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Extended handshake is sent during connection initialization procedure
 * by peers that support BEP-10: Extension Protocol.
 * It contains a dictionary of supported extended message types with
 * their corresponding numeric IDs, as well as any additional information,
 * that is specific to concrete BitTorrent clients and BEPs,
 * that utilize extended messaging.
 *
 * @since 1.0
 */
public final class ExtendedHandshake extends ExtendedMessage {

    /**
     * Message type mapping key in the extended handshake.
     *
     * @since 1.0
     */
    public static final String MESSAGE_TYPE_MAPPING_KEY = "m";

    /**
     * @since 1.0
     */
    public static Builder builder() {
        return new Builder();
    }

    private Map<String, BEObject<?>> data;
    private Set<String> supportedMessageTypes;

    ExtendedHandshake(Map<String, BEObject<?>> data) {
        this.data = Collections.unmodifiableMap(data);

        BEMap supportedMessageTypes = (BEMap) data.get(MESSAGE_TYPE_MAPPING_KEY);
        if (supportedMessageTypes != null) {
            this.supportedMessageTypes = Collections.unmodifiableSet(supportedMessageTypes.getValue().keySet());
        } else {
            this.supportedMessageTypes = Collections.emptySet();
        }
    }

    /**
     * @return Payload of this extended handshake.
     * @since 1.0
     */
    public Map<String, BEObject<?>> getData() {
        return data;
    }

    /**
     * @return Set of message type names, that are specified
     *         in this handshake's message type mapping.
     * @since 1.0
     */
    public Set<String> getSupportedMessageTypes() {
        return supportedMessageTypes;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] supported messages {" + supportedMessageTypes + "}, data {" + data + "}";
    }

    /**
     * Convenient builder that provides the means
     * to build an extended handshake by specifying
     * one message type mapping at a time.
     *
     * @since 1.0
     */
    public static class Builder {

        private Map<String, BEObject<?>> messageTypeMap;
        private Map<String, BEObject<?>> data;

        private Builder() {
            data = new HashMap<>();
        }

        public Builder property(String name, BEObject<?> value) {
            Objects.requireNonNull(name);
            if (MESSAGE_TYPE_MAPPING_KEY.equals(name)) {
                throw new IllegalArgumentException("Property name is reserved: " + MESSAGE_TYPE_MAPPING_KEY);
            }

            Objects.requireNonNull(value);
            data.put(name, value);
            return this;
        }

        /**
         * Adds a mapping between message type name and its' numeric ID.
         *
         * @param typeName Message type name
         * @param typeId Numeric message type ID
         * @since 1.0
         */
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

        /**
         * @since 1.0
         */
        public ExtendedHandshake build() {

            if (messageTypeMap != null) {
                data.put(MESSAGE_TYPE_MAPPING_KEY, new BEMap(null, messageTypeMap));
            }
            return new ExtendedHandshake(data);
        }
    }
}
