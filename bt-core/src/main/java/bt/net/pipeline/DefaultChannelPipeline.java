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

package bt.net.pipeline;

import bt.net.Peer;
import bt.net.buffer.BorrowedBuffer;
import bt.net.buffer.BufferMutator;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class DefaultChannelPipeline implements ChannelPipeline {

    private final MessageDeserializer deserializer;
    private final MessageSerializer serializer;

    private final BorrowedBuffer<ByteBuffer> inboundBuffer;
    private final BorrowedBuffer<ByteBuffer> outboundBuffer;
    private final List<BufferMutator> decoders;
    private final List<BufferMutator> encoders;

    private final Queue<Message> inboundQueue;

    // inbound buffer parameters
    private int decodedDataOffset;
    private int undecodedDataOffset;

    private DefaultChannelHandlerContext context;

    public DefaultChannelPipeline(
            Peer peer,
            MessageHandler<Message> protocol,
            BorrowedBuffer<ByteBuffer> inboundBuffer,
            BorrowedBuffer<ByteBuffer> outboundBuffer,
            List<BufferMutator> decoders,
            List<BufferMutator> encoders) {

        this.deserializer = new MessageDeserializer(peer, protocol);
        this.serializer = new MessageSerializer(peer, protocol);

        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;

        this.decoders = decoders;
        this.encoders = encoders;
        this.inboundQueue = new LinkedBlockingQueue<>();

        // process existing data immediately (e.g. there might be leftovers from MSE handshake)
        fireDataReceived();
    }

    @Override
    public Message decode() {
        checkHandlerIsBound();

        return inboundQueue.poll();
    }

    private void fireDataReceived() {
        ByteBuffer buffer = inboundBuffer.lockAndGet();
        try {
            processInboundData(buffer);
        } finally {
            inboundBuffer.unlock();
        }
    }

    private void processInboundData(ByteBuffer buffer) {
        int undecodedDataLimit = buffer.position();
        if (undecodedDataOffset < undecodedDataLimit) {
            buffer.flip();
            decoders.forEach(mutator -> {
                buffer.position(undecodedDataOffset);
                mutator.mutate(buffer);
            });
            undecodedDataOffset = undecodedDataLimit;

            buffer.position(decodedDataOffset);
            buffer.limit(undecodedDataOffset);
            Message message;
            for (;;) {
                message = deserializer.deserialize(buffer);
                if (message == null) {
                    break;
                } else {
                    inboundQueue.add(message);
                    decodedDataOffset = buffer.position();
                }
            }

            buffer.clear();
            buffer.position(undecodedDataLimit);
            if (!buffer.hasRemaining()) {
                buffer.position(decodedDataOffset);
                buffer.compact();
                undecodedDataOffset -= decodedDataOffset;
                buffer.position(undecodedDataOffset);
                decodedDataOffset = 0;
            }
        }
    }

    @Override
    public boolean encode(Message message) {
        checkHandlerIsBound();

        ByteBuffer buffer = outboundBuffer.lockAndGet();
        if (buffer == null) {
            // buffer has been released
            return false;
        }

        try {
            return writeMessageToBuffer(message, buffer);
        } finally {
            outboundBuffer.unlock();
        }
    }

    private boolean writeMessageToBuffer(Message message, ByteBuffer buffer) {
        buffer.clear();
        boolean written = serializer.serialize(message, buffer);
        if (written) {
            encoders.forEach(mutator -> {
                buffer.flip();
                mutator.mutate(buffer);
            });
            buffer.flip();
        }
        return written;
    }

    private void checkHandlerIsBound() {
        if (context == null) {
            throw new IllegalStateException("Channel handler is not bound");
        }
    }

    @Override
    public ChannelHandlerContext bindHandler(ChannelHandler handler) {
        if (context != null) {
            if (handler == context.handler()) {
                return context;
            } else {
                throw new IllegalStateException("Already bound to different handler");
            }
        }

        context = new DefaultChannelHandlerContext(handler, this);
        return context;
    }

    private class DefaultChannelHandlerContext implements ChannelHandlerContext {

        private final ChannelHandler handler;
        private final DefaultChannelPipeline pipeline;

        DefaultChannelHandlerContext(ChannelHandler handler, DefaultChannelPipeline pipeline) {
            this.handler = handler;
            this.pipeline = pipeline;
        }

        ChannelHandler handler() {
            return handler;
        }

        @Override
        public ChannelPipeline pipeline() {
            return pipeline;
        }

        @Override
        public void fireChannelReady() {
            handler.read();
        }

        @Override
        public void fireChannelRegistered() {
            // TODO
        }

        @Override
        public void fireChannelUnregistered() {
            // TODO
        }

        @Override
        public void fireChannelActive() {
            // TODO
        }

        @Override
        public void fireChannelInactive() {
            // TODO
        }

        @Override
        public void fireDataReceived() {
            pipeline.fireDataReceived();
        }
    }
}
