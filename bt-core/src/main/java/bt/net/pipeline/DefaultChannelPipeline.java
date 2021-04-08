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

public class DefaultChannelPipeline implements ChannelPipeline {

    private final InboundMessageProcessor inboundMessageProcessor;
    private final MessageSerializer serializer;

    private final BorrowedBuffer<ByteBuffer> inboundBuffer;
    private final BorrowedBuffer<ByteBuffer> outboundBuffer;
    private final List<BufferMutator> encoders;

    private DefaultChannelHandlerContext context;

    public DefaultChannelPipeline(
            Peer peer,
            MessageHandler<Message> protocol,
            BorrowedBuffer<ByteBuffer> inboundBuffer,
            BorrowedBuffer<ByteBuffer> outboundBuffer,
            List<BufferMutator> decoders,
            List<BufferMutator> encoders,
            IBufferedPieceRegistry bufferedPieceRegistry) {

        ByteBuffer buffer;
        try {
            buffer = inboundBuffer.lockAndGet();
        } finally {
            inboundBuffer.unlock();
        }

        this.inboundMessageProcessor = new InboundMessageProcessor(peer, buffer,
                new MessageDeserializer(peer, protocol), decoders, bufferedPieceRegistry);
        this.serializer = new MessageSerializer(peer, protocol);
        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;
        this.encoders = encoders;

        // process existing data immediately (e.g. there might be leftovers from MSE handshake)
        fireDataReceived();
    }

    @Override
    public Message decode() {
        checkHandlerIsBound();

        return inboundMessageProcessor.pollMessage();
    }

    private void fireDataReceived() {
        try {
            inboundBuffer.lockAndGet();
            inboundMessageProcessor.processInboundData();
        } finally {
            inboundBuffer.unlock();
        }
    }

    @Override
    public boolean encode(Message message) {
        checkHandlerIsBound();

        ByteBuffer buffer = outboundBuffer.lockAndGet();
        if (buffer == null) {
            // buffer has been released
            // TODO: So what? Maybe throw an exception then?
            // When remove peer closed connection(for duplicate connections), buffer is been released.
            // 2021-04-08 13:25:09.654 ERROR [6881.bt.net.data-receiver] bt.net.pipeline.SocketChannelHandler.read:85 null
            // java.io.EOFException: null
            //      at bt.net.pipeline.SocketChannelHandler.processInboundData(SocketChannelHandler.java:125)
            //      at bt.net.pipeline.SocketChannelHandler.read(SocketChannelHandler.java:82)
            //      at bt.net.pipeline.DefaultChannelPipeline$DefaultChannelHandlerContext.readFromChannel(DefaultChannelPipeline.java:157)
            //      at bt.net.DataReceivingLoop.processKey(DataReceivingLoop.java:187)
            //      at bt.net.DataReceivingLoop.run(DataReceivingLoop.java:128)
            //      at bt.net.DataReceivingLoop.lambda$null$1(DataReceivingLoop.java:65)
            //      at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
            //      at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
            //      at java.lang.Thread.run(Thread.java:748)
            // 2021-04-08 13:25:09.653 INFO  [6881.bt.net.data-receiver] bt.net.pipeline.SocketChannelHandler.shutdown:180 shutdown
            // java.lang.RuntimeException: null
            //      at bt.net.pipeline.SocketChannelHandler.shutdown(SocketChannelHandler.java:180)
            //      at bt.net.pipeline.SocketChannelHandler.read(SocketChannelHandler.java:84)
            //      at bt.net.pipeline.DefaultChannelPipeline$DefaultChannelHandlerContext.readFromChannel(DefaultChannelPipeline.java:157)
            //      at bt.net.DataReceivingLoop.processKey(DataReceivingLoop.java:187)
            //      at bt.net.DataReceivingLoop.run(DataReceivingLoop.java:128)
            //      at bt.net.DataReceivingLoop.lambda$null$1(DataReceivingLoop.java:65)
            //      at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
            //      at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
            //      at java.lang.Thread.run(Thread.java:748)
            outboundBuffer.unlock();
            return false;
        }

        try {
            return writeMessageToBuffer(message, buffer);
        } finally {
            outboundBuffer.unlock();
        }
    }

    private boolean writeMessageToBuffer(Message message, ByteBuffer buffer) {
        int encodedDataLimit = buffer.position();
        boolean written = serializer.serialize(message, buffer);
        if (written) {
            int unencodedDataLimit = buffer.position();
            buffer.flip();
            encoders.forEach(mutator -> {
                buffer.position(encodedDataLimit);
                mutator.mutate(buffer);
            });
            buffer.clear();
            buffer.position(unencodedDataLimit);
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
        public boolean readFromChannel() {
            return handler.read();
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
