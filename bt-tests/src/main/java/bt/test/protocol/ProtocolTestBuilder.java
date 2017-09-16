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

package bt.test.protocol;

import bt.net.Peer;
import bt.protocol.Bitfield;
import bt.protocol.Cancel;
import bt.protocol.DecodingContext;
import bt.protocol.Handshake;
import bt.protocol.Have;
import bt.protocol.Message;
import bt.protocol.Piece;
import bt.protocol.Request;
import bt.protocol.StandardBittorrentProtocol;
import bt.protocol.handler.MessageHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

/**
 * Provides the means to build a custom protocol test.
 *
 * @since 1.1
 */
public class ProtocolTestBuilder {

    private ProtocolBuilder protocolBuilder;
    private Supplier<DecodingContext> decodingContextSupplier;
    private Map<Class<? extends Message>, BiPredicate<?, ?>> matchers;

    ProtocolTestBuilder() {
        this.matchers = defaultMatchers();
        this.protocolBuilder = new ProtocolBuilder() {

            private Map<Integer, MessageHandler<?>> extraHandlers;

            @Override
            public ProtocolBuilder addMessageHandler(Integer messageTypeId, MessageHandler<?> handler) {
                Objects.requireNonNull(messageTypeId);
                Objects.requireNonNull(handler);

                if (extraHandlers == null) {
                    extraHandlers = new HashMap<>();
                }
                extraHandlers.put(messageTypeId, handler);
                return this;
            }

            @Override
            public MessageHandler<Message> build() {
                if (extraHandlers == null) {
                    extraHandlers = Collections.emptyMap();
                }
                return new StandardBittorrentProtocol(extraHandlers);
            }
        };
    }

    ProtocolTestBuilder(MessageHandler<Message> protocol) {
        Objects.requireNonNull(protocol);

        this.protocolBuilder = new ProtocolBuilder() {
            @Override
            public ProtocolBuilder addMessageHandler(Integer messageTypeId, MessageHandler<?> handler) {
                throw new UnsupportedOperationException("");
            }

            @Override
            public MessageHandler<Message> build() {
                return protocol;
            }
        };
    }

    private Map<Class<? extends Message>, BiPredicate<?, ?>> defaultMatchers() {
        return new HashMap<Class<? extends Message>, BiPredicate<?, ?>>() {{
            put(Bitfield.class, new BitfieldMatcher());
            put(Cancel.class, new CancelMatcher());
            put(Handshake.class, new HandshakeMatcher());
            put(Have.class, new HaveMatcher());
            put(Piece.class, new PieceMatcher());
            put(Request.class, new RequestMatcher());
        }};
    }

    /**
     * Provide an extra message handler.
     *
     * @param messageTypeId Unique numeric ID of the message type, supported by the provided handler
     * @param handler Message handler
     * @see #matcher(Class, BiPredicate)
     * @since 1.1
     */
    public ProtocolTestBuilder extraMessageHandler(Integer messageTypeId, MessageHandler<?> handler) {
        protocolBuilder.addMessageHandler(messageTypeId, handler);
        return this;
    }

    /**
     * Provide a custom decoding context supplier.
     *
     * @param decodingContextSupplier Supplier of decoding contexts
     * @since 1.1
     */
    public ProtocolTestBuilder decodingContextSupplier(Supplier<DecodingContext> decodingContextSupplier) {
        this.decodingContextSupplier = Objects.requireNonNull(decodingContextSupplier);
        return this;
    }

    /**
     * Provide a custom message matcher.
     * Matchers are used by certain protocol test methods to make assertions about the decoded messages.
     *
     * @param messageType Message type
     * @param matcher Matcher
     * @see #extraMessageHandler(Integer, MessageHandler)
     * @since 1.1
     */
    public <T extends Message> ProtocolTestBuilder matcher(Class<T> messageType, BiPredicate<T, T> matcher) {
        Objects.requireNonNull(messageType);
        Objects.requireNonNull(matcher);

        if (matchers == null) {
            matchers = new HashMap<>();
        }
        matchers.put(messageType, matcher);
        return this;
    }

    /**
     * @since 1.1
     */
    public ProtocolTest build() {
        if (matchers == null) {
            matchers = Collections.emptyMap();
        }
        if (decodingContextSupplier == null) {
            decodingContextSupplier = () -> new DecodingContext(mock(Peer.class));
        }
        return new ProtocolTest(protocolBuilder.build(), decodingContextSupplier, matchers);
    }
}
