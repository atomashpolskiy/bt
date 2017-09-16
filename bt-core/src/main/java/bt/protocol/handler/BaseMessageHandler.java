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

package bt.protocol.handler;

import bt.BtException;
import bt.protocol.EncodingContext;
import bt.protocol.Message;
import bt.protocol.DecodingContext;
import bt.protocol.StandardBittorrentProtocol;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Objects;

import static bt.protocol.Protocols.readInt;
import static bt.protocol.StandardBittorrentProtocol.MESSAGE_PREFIX_SIZE;
import static bt.protocol.StandardBittorrentProtocol.MESSAGE_TYPE_SIZE;

/**
 * Base class for {@link MessageHandler} implementations, that work with a single message type.
 *
 * @param <T> Message type, that this handler is able to encode and decode.
 * @since 1.0
 */
public abstract class BaseMessageHandler<T extends Message> implements MessageHandler<T> {

    @Override
    public boolean encode(EncodingContext context, T message, ByteBuffer buffer) {

        if (buffer.remaining() < MESSAGE_PREFIX_SIZE) {
            return false;
        }

        int begin = buffer.position();
        buffer.position(begin + MESSAGE_PREFIX_SIZE);
        if (doEncode(context, message, buffer)) {
            int end = buffer.position();
            int payloadLength = end - begin - MESSAGE_PREFIX_SIZE;
            if (payloadLength < 0) {
                throw new BtException("Unexpected payload length: " + payloadLength);
            }
            buffer.position(begin);
            buffer.putInt(payloadLength + MESSAGE_TYPE_SIZE);
            buffer.put(message.getMessageId().byteValue());
            buffer.position(end);
            return true;
        } else {
            buffer.position(begin);
            return false;
        }
    }

    /**
     * Encode the payload of a message (excluding message prefix -- see {@link StandardBittorrentProtocol#MESSAGE_PREFIX_SIZE})
     * and write it into the provided buffer.
     *
     * @param context Encoding context
     * @param buffer Byte buffer to write to.
     *               Encoded message should be placed into the buffer starting with its current position.
     *               This method should check if the buffer has sufficient space available, and return false
     *               if it's not the case.
     *               After this method returns, the buffer's {@link Buffer#position()} is used
     *               to calculate the size of message's payload, which is then specified in the message's prefix.
     * @return true if message has been successfully encoded and fully written into the provided buffer
     * @since 1.3
     */
    protected abstract boolean doEncode(EncodingContext context, T message, ByteBuffer buffer);

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {

        if (buffer.remaining() < MESSAGE_PREFIX_SIZE) {
            return 0;
        }

        Integer length = Objects.requireNonNull(readInt(buffer));
        if (buffer.remaining() < length) {
            return 0;
        }
        buffer.get(); // skip message ID

        int initialLimit = buffer.limit();
        buffer.limit(buffer.position() + length - MESSAGE_TYPE_SIZE);
        try {
            int consumed = doDecode(context, buffer);
            if (context.getMessage() != null) {
                return consumed + MESSAGE_PREFIX_SIZE;
            }
        } finally {
            buffer.limit(initialLimit);
        }
        return 0;
    }

    /**
     * Decode the payload of a message (excluding message prefix -- see {@link StandardBittorrentProtocol#MESSAGE_PREFIX_SIZE})
     * and place it into the provided context.
     *
     * @param context The context to place the decoded message into.
     * @param buffer Buffer to decode from. {@link Buffer#remaining()} is set
     *               to the declared length of the message.
     *               Message payload starts precisely at buffer's {@link Buffer#position()}.
     * @since 1.0
     */
    protected abstract int doDecode(DecodingContext context, ByteBuffer buffer);
}
