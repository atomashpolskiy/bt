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

import bt.net.DataReceiver;
import bt.net.buffer.BorrowedBuffer;
import bt.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class SocketChannelHandler implements ChannelHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketChannelHandler.class);

    private final SocketChannel channel;
    private final BorrowedBuffer<ByteBuffer> inboundBuffer;
    private final BorrowedBuffer<ByteBuffer> outboundBuffer;
    private final ChannelHandlerContext context;
    private final DataReceiver dataReceiver;

    private final Object inboundBufferLock;
    private final Object outboundBufferLock;
    private final AtomicBoolean shutdown;

    public SocketChannelHandler(
            SocketChannel channel,
            BorrowedBuffer<ByteBuffer> inboundBuffer,
            BorrowedBuffer<ByteBuffer> outboundBuffer,
            Function<ChannelHandler, ChannelHandlerContext> contextFactory,
            DataReceiver dataReceiver) {

        this.channel = channel;
        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;
        this.context = contextFactory.apply(this);
        this.dataReceiver = dataReceiver;

        this.inboundBufferLock = new Object();
        this.outboundBufferLock = new Object();
        this.shutdown = new AtomicBoolean(false);
    }

    @Override
    public void send(Message message) {
        if (!context.pipeline().encode(message)) {
            flush();
            if (!context.pipeline().encode(message)) {
                throw new IllegalStateException("Failed to send message: " + message);
            }
        }
        flush();
    }

    @Override
    public Message receive() {
        return context.pipeline().decode();
    }

    @Override
    public boolean read() {
        try {
            return processInboundData();
        } catch (Exception e) {
            shutdown();
            throw new RuntimeException("Unexpected error", e);
        }
    }

    @Override
    public void register() {
        dataReceiver.registerChannel(channel, context);
        context.fireChannelRegistered();
    }

    @Override
    public void unregister() {
        dataReceiver.unregisterChannel(channel);
        context.fireChannelUnregistered();
    }

    @Override
    public void activate() {
        dataReceiver.activateChannel(channel);
        context.fireChannelActive();
    }

    @Override
    public void deactivate() {
        dataReceiver.deactivateChannel(channel);
        context.fireChannelInactive();
    }

    private boolean processInboundData() throws IOException {
        synchronized (inboundBufferLock) {
            ByteBuffer buffer = inboundBuffer.lockAndGet();
            try {
                do {
                    int readLast;
                    while ((readLast = channel.read(buffer)) > 0)
                        ;
                    boolean insufficientSpace = !buffer.hasRemaining();
                    context.fireDataReceived();
                    if (readLast == -1) {
                        throw new EOFException();
                    } else if (!insufficientSpace) {
                        return true;
                    }
                } while (buffer.hasRemaining());
                return false;
            } finally {
                inboundBuffer.unlock();
            }
        }
    }

    @Override
    public void flush() {
        synchronized (outboundBufferLock) {
            ByteBuffer buffer = outboundBuffer.lockAndGet();
            if (buffer == null) {
                // buffer has been released
                return;
            }
            buffer.flip();
            try {
                while (buffer.hasRemaining()) {
                    int written = channel.write(buffer);
                    // for unknown reason, -2 is returned of every call to channel.write, and no data is written, buffer.hasRemaining() is always true.
                    // thus program falls into dead loop.
                    if (written < 0) {
                        throw new IOException("Write method returns " + written);
                    }
                }
                buffer.compact();
                outboundBuffer.unlock();
            } catch (IOException e) {
                outboundBuffer.unlock(); // can't use finally block due to possibility of double-unlock
                shutdown();
                throw new RuntimeException("Unexpected I/O error", e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (inboundBufferLock) {
            synchronized (outboundBufferLock) {
                shutdown();
            }
        }
    }

    private void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            try {
                unregister();
            } catch (Exception e) {
                LOGGER.error("Failed to unregister channel", e);
            }
            closeChannel();
            releaseBuffers();
        }
    }

    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close channel", e);
        }
    }

    private void releaseBuffers() {
        releaseBuffer(inboundBuffer);
        releaseBuffer(outboundBuffer);
    }

    private void releaseBuffer(BorrowedBuffer<ByteBuffer> buffer) {
        try {
            buffer.release();
        } catch (Exception e) {
            LOGGER.error("Failed to release buffer", e);
        }
    }

    @Override
    public boolean isClosed() {
        return shutdown.get();
    }
}
