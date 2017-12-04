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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SocketChannelHandler implements ChannelHandler {

    private final Peer peer;
    private final SocketChannel channel;
    private final ByteBuffer inboundBuffer;
    private final ByteBuffer outboundBuffer;
    private final ChannelPipeline pipeline;

    public SocketChannelHandler(
            Peer peer,
            SocketChannel channel,
            ByteBuffer inboundBuffer,
            ByteBuffer outboundBuffer,
            ChannelPipeline pipeline) {

        this.peer = peer;
        this.channel = channel;
        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;
        this.pipeline = pipeline;
    }

    @Override
    public Peer peer() {
        return peer;
    }

    @Override
    public void fireChannelReady() {
        try {
            channel.read(inboundBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fireChannelRegistered() {

    }

    public void fireChannelUnregistered() {

    }

    @Override
    public void fireChannelActive() {

    }

    @Override
    public void fireChannelInactive() {

    }
}
