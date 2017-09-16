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

import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Provides convenient means for simplifying creation of protocol test suites.
 *
 * @since 1.1
 */
public class ProtocolTest {

    /**
     * Create a builder for the standard BitTorrent protocol.
     *
     * @since 1.1
     */
    public static ProtocolTestBuilder forBittorrentProtocol() {
        return new ProtocolTestBuilder();
    }

    /**
     * Create a builder for a custom protocol.
     *
     * @since 1.1
     */
    public static ProtocolTestBuilder forProtocol(MessageHandler<Message> protocol) {
        return new ProtocolTestBuilder(protocol);
    }

    private final MessageHandler<Message> protocol;
    private final Supplier<DecodingContext> decodingContextSupplier;
    private final Map<Class<? extends Message>, BiPredicate<?, ?>> matchers;
    private final BiPredicate<Message, Message> defaultMatcher;

    ProtocolTest(MessageHandler<Message> protocol,
                 Supplier<DecodingContext> decodingContextSupplier,
                 Map<Class<? extends Message>, BiPredicate<?, ?>> matchers) {
        this.protocol = protocol;
        this.decodingContextSupplier = decodingContextSupplier;
        this.matchers = matchers;
        this.defaultMatcher = new EqualityMatcher<>();
    }

    /**
     * @since 1.1
     */
    public MessageHandler<Message> getProtocol() {
        return protocol;
    }

    /**
     * @since 1.1
     */
    public DecodingContext createDecodingContext() {
        return decodingContextSupplier.get();
    }

    /**
     * Assert that the provided data does not contain enough bytes for successful decoding
     * and the message type can not be determined.
     *
     * @param data Raw message data (most probably truncated)
     * @since 1.1
     */
    public void assertInsufficientDataAndNothingConsumed(byte[] data) throws Exception {
        assertInsufficientDataAndNothingConsumed(null, data);
    }

    /**
     * Assert that the provided data does not contain enough bytes for successful decoding
     * and the message type is equal to the expected type.
     *
     * When the expected type is {@code null}, this method behaves exactly
     * as its' overloaded version {@link #assertInsufficientDataAndNothingConsumed(byte[])}.
     *
     * @param expectedType Expected message type
     * @param data Raw message data (most probably truncated)
     * @since 1.1
     */
    public void assertInsufficientDataAndNothingConsumed(Class<? extends Message> expectedType,
                                                         byte[] data) throws Exception {

        ByteBuffer buffer = ByteBuffer.wrap(data).asReadOnlyBuffer();
        buffer.mark();

        byte[] copy = Arrays.copyOf(data, data.length);

        Class<? extends Message> actualType = protocol.readMessageType(buffer);
        buffer.reset();
        if (expectedType == null) {
            assertNull(actualType);
        } else {
            assertEquals(expectedType, actualType);
        }

        DecodingContext context = decodingContextSupplier.get();
        int consumed = protocol.decode(context, buffer);
        buffer.reset();

        // check that buffer is not changed
        assertArrayEquals(copy, data);

        assertEquals(0, consumed);

        Message decoded = context.getMessage();
        assertNull(decoded);
    }

    /**
     * Assert that the provided data contains sufficient bytes for successful decoding
     * and that the decoded message matches the expected message.
     *
     * @param expectedBytesConsumed Exact number of bytes that is expected to be consumed during the decoding
     * @param expectedMessage Expected message
     * @param data Raw message data
     * @since 1.1
     */
    public void assertDecoded(int expectedBytesConsumed,
                              Message expectedMessage,
                              byte[] data) throws Exception {

        ByteBuffer in = ByteBuffer.wrap(data).asReadOnlyBuffer();
        in.mark();

        assertEquals(expectedMessage.getClass(), protocol.readMessageType(in));
        in.reset();

        DecodingContext context = decodingContextSupplier.get();
        int consumed = protocol.decode(context, in);
        in.reset();

        assertEquals(expectedBytesConsumed, consumed);

        Message decodedMessage = context.getMessage();
        assertMessageEquals(expectedMessage, decodedMessage);

        ByteBuffer out = ByteBuffer.allocate(expectedBytesConsumed);
        out.mark();
        assertTrue("Protocol failed to serialize message of length: " + expectedBytesConsumed,
                protocol.encode(new EncodingContext(null), decodedMessage, out));
        assertEquals(expectedBytesConsumed, out.position());

        byte[] encoded = new byte[expectedBytesConsumed];
        out.reset();
        out.get(encoded);

        if (data.length > expectedBytesConsumed) {
            data = Arrays.copyOfRange(data, 0, expectedBytesConsumed);
        }
        assertArrayEquals(data, encoded);
    }

    @SuppressWarnings("unchecked")
    private void assertMessageEquals(Message expected, Message actual) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            throw new AssertionError("One of the messages is null and the other is not");
        }

        Class<? extends Message> expectedType = expected.getClass(), actualType = actual.getClass();
        assertEquals(String.format("Message types are different (expected: '%s', actual: '%s')", expectedType, actualType),
                expectedType, actualType);

        BiPredicate<Message, Message> predicate = (BiPredicate<Message, Message>) matchers.get(expectedType);
        if (predicate != null) {
            assertTrue(predicate.test(expected, actual));
        } else {
            assertTrue("Messages of type '" + expectedType.getName() + "' do not match", defaultMatcher.test(expected, actual));
        }
    }
}
