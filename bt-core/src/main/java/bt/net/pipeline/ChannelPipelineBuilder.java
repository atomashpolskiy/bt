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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class ChannelPipelineBuilder {

    private final Peer peer;
    private ByteChannel channel;
    private MessageHandler<Message> protocol;
    private ByteBuffer inboundBuffer;
    private ByteBuffer outboundBuffer;
    private List<BufferMutator> inboundMutators;
    private List<BufferMutator> outboundMutators;

    ChannelPipelineBuilder(Peer peer) {
        this.peer = Objects.requireNonNull(peer);
    }

    public ChannelPipelineBuilder channel(ByteChannel channel) {
        this.channel = Objects.requireNonNull(channel);
        return this;
    }

    public ChannelPipelineBuilder protocol(MessageHandler<Message> protocol) {
        this.protocol = Objects.requireNonNull(protocol);
        return this;
    }

    public ChannelPipelineBuilder inboundBuffer(ByteBuffer inboundBuffer) {
        this.inboundBuffer = Objects.requireNonNull(inboundBuffer);
        return this;
    }

    public ChannelPipelineBuilder outboundBuffer(ByteBuffer outboundBuffer) {
        this.outboundBuffer = Objects.requireNonNull(outboundBuffer);
        return this;
    }

    public ChannelPipelineBuilder inboundMutators(BufferMutator mutator, BufferMutator... otherMutators) {
        Objects.requireNonNull(mutator);
        inboundMutators = new ArrayList<>();
        inboundMutators.add(mutator);
        if (otherMutators != null) {
            inboundMutators.addAll(Arrays.asList(otherMutators));
        }
        return this;
    }

    public ChannelPipelineBuilder outboundMutators(BufferMutator mutator, BufferMutator... otherMutators) {
        Objects.requireNonNull(mutator);
        outboundMutators = new ArrayList<>();
        outboundMutators.add(mutator);
        if (otherMutators != null) {
            outboundMutators.addAll(Arrays.asList(otherMutators));
        }
        return this;
    }

    public ChannelPipeline build() {
        Objects.requireNonNull(channel, "Missing channel");
        Objects.requireNonNull(protocol, "Missing protocol");

        Optional<ByteBuffer> _inboundBuffer = Optional.ofNullable(inboundBuffer);
        Optional<ByteBuffer> _outboundBuffer = Optional.ofNullable(outboundBuffer);
        List<BufferMutator> _inboundMutators = (inboundMutators == null) ? Collections.emptyList() : inboundMutators;
        List<BufferMutator> _outboundMutators = (outboundMutators == null) ? Collections.emptyList() : outboundMutators;

        return doBuild(peer, channel, protocol, _inboundBuffer, _outboundBuffer, _inboundMutators, _outboundMutators);
    }

    protected abstract ChannelPipeline doBuild(
            Peer peer,
            ByteChannel channel,
            MessageHandler<Message> protocol,
            Optional<ByteBuffer> inboundBuffer,
            Optional<ByteBuffer> outboundBuffer,
            List<BufferMutator> inboundMutators,
            List<BufferMutator> outboundMutators);
}
