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

package bt.net;

import bt.protocol.EncodingContext;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Encodes and writes messages to a byte channel.
 *
 * Note that this class is not a part of the public API and is subject to change.
 *
 * @since 1.2
 */
class MessageWriter {

    private static final int WRITE_ATTEMPTS = 10;

    private final WritableByteChannel channel;
    private final EncodingContext context;
    private final MessageHandler<Message> messageHandler;

    private final ByteBuffer buffer;

    /**
     * Create a message writer with a private buffer
     *
     * @param channel Writable byte channel
     * @param peer Peer
     * @param messageHandler Message encoder
     * @param bufferSize Size of the internal buffer, that will be used to store encoded but not yet sent messages.
     * @since 1.3
     */
    public MessageWriter(WritableByteChannel channel,
                         Peer peer,
                         MessageHandler<Message> messageHandler,
                         int bufferSize) {
        this(channel, peer, messageHandler, ByteBuffer.allocateDirect(bufferSize));
    }

    /**
     * Create a message writer with the provided buffer
     *
     * @param channel Writable byte channel
     * @param peer Peer
     * @param messageHandler Message encoder
     * @param buffer Buffer, that will be used to store encoded but not yet sent messages.
     * @since 1.2
     */
    public MessageWriter(WritableByteChannel channel,
                         Peer peer,
                         MessageHandler<Message> messageHandler,
                         ByteBuffer buffer) {
        this.channel = channel;
        this.context = new EncodingContext(peer);
        this.messageHandler = messageHandler;
        this.buffer = buffer;
    }

    public void writeMessage(Message message) throws IOException {
        buffer.clear();
        if (!writeToBuffer(message, buffer)) {
            throw new IllegalStateException("Insufficient space in buffer for message: " + message);
        }
        buffer.flip();
        writeMessageFromBuffer();
    }

    protected boolean writeToBuffer(Message message, ByteBuffer buffer) {
        return messageHandler.encode(context, message, buffer);
    }

    private void writeMessageFromBuffer() throws IOException {
        int offset = buffer.position();
        int written;
        int k = 0;
        do {
            buffer.position(offset);
            written = channel.write(buffer);
            offset = offset + written;

            if (offset < buffer.limit()) {
                if (++k <= WRITE_ATTEMPTS) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted while writing message", e);
                    }
                } else {
                    throw new RuntimeException("Failed to write message in " + WRITE_ATTEMPTS + " attempts");
                }
            }
        } while (offset < buffer.limit());
    }
}
