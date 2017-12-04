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
import bt.net.buffer.BufferMutator;
import bt.protocol.Message;
import bt.protocol.handler.MessageHandler;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.List;

public class DefaultChannelPipeline implements ChannelPipeline {

    private final ByteChannel channel;

    private final MessageReader reader;
    private final MessageWriter writer;

    private final ByteBuffer inboundBuffer;
    private final ByteBuffer outboundBuffer;
    private final List<BufferMutator> inboundMutators;
    private final List<BufferMutator> outboundMutators;

    public DefaultChannelPipeline(
            Peer peer,
            ByteChannel channel,
            MessageHandler<Message> protocol,
            ByteBuffer inboundBuffer,
            ByteBuffer outboundBuffer,
            List<BufferMutator> inboundMutators,
            List<BufferMutator> outboundMutators) {

        this.channel = channel;
        this.reader = new MessageReader(peer, channel, protocol, inboundBuffer);
        this.writer = new MessageWriter(channel, peer, protocol, outboundBuffer);
        this.inboundBuffer = inboundBuffer;
        this.outboundBuffer = outboundBuffer;
        this.inboundMutators = inboundMutators;
        this.outboundMutators = outboundMutators;
    }

    @Override
    public Message receive() {


        return null;
    }

    @Override
    public boolean send(Message message) {
        return false;
    }
}
