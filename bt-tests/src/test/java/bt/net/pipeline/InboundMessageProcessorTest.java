/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

import bt.metainfo.TorrentId;
import bt.net.InetPeer;
import bt.net.PeerId;
import bt.protocol.*;
import bt.protocol.handler.MessageHandler;
import bt.test.protocol.ProtocolTest;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class InboundMessageProcessorTest {

    private EncodingContext encodingContext;
    private MessageHandler<Message> protocol;
    private ByteBuffer buffer;
    private InboundMessageProcessor processor;

    @Before
    public void setUp() {
        this.buffer = ByteBuffer.allocate(100);
        InetPeer peer = new InetPeer(InetAddress.getLoopbackAddress(), 9999);
        this.encodingContext = new EncodingContext(peer);
        this.protocol = ProtocolTest.forBittorrentProtocol().build().getProtocol();
        MessageDeserializer deserializer = new MessageDeserializer(peer, protocol);
        this.processor = new InboundMessageProcessor(buffer, deserializer, Collections.emptyList());
    }

    @Test
    public void test() {
        processor.processInboundData();

        // Write several messages fully
        encodeToBuffer(new Handshake(new byte[8], TorrentId.fromBytes(new byte[20]), PeerId.fromBytes(new byte[20])));
        encodeToBuffer(new Bitfield(new byte[10]));
//        encodeToBuffer(new Have(0));

        processor.processInboundData();
        assertTrue(processor.pollMessage() instanceof Handshake);
        assertTrue(processor.pollMessage() instanceof Bitfield);
//        assertTrue(processor.pollMessage() instanceof Have);
        /////////////////////////////////////////////////////////////

        // Write one message in two parts
        ByteBuffer tempBuffer = ByteBuffer.allocate(100);
        encodeToBuffer(new Have(1), tempBuffer);
        tempBuffer.flip();
        byte[] bytes0 = new byte[tempBuffer.remaining()];
        tempBuffer.get(bytes0);

        buffer.put(bytes0, 0, 1);
        processor.processInboundData();

        buffer.put(bytes0, 1, bytes0.length - 1);
        processor.processInboundData();

        assertTrue(processor.pollMessage() instanceof Have);
        /////////////////////////////////////////////////////////////

        // Write several messages fully with partial trailing message
        encodeToBuffer(new Handshake(new byte[8], TorrentId.fromBytes(new byte[20]), PeerId.fromBytes(new byte[20])));
        encodeToBuffer(new Bitfield(new byte[10]));

        tempBuffer.clear();
        encodeToBuffer(new Have(1), tempBuffer);
        tempBuffer.flip();
        byte[] bytes1 = new byte[tempBuffer.remaining()];
        tempBuffer.get(bytes1);

        buffer.put(bytes1, 0, 1);
        processor.processInboundData();

        buffer.put(bytes1, 1, bytes1.length - 2);
        processor.processInboundData();

        buffer.put(bytes1[bytes1.length - 1]);
        processor.processInboundData();

        assertTrue(processor.pollMessage() instanceof Handshake);
        assertTrue(processor.pollMessage() instanceof Bitfield);
        assertTrue(processor.pollMessage() instanceof Have);
        /////////////////////////////////////////////////////////////

        // Write several messages fully
        encodeToBuffer(new Bitfield(new byte[10]));
        encodeToBuffer(new Have(0));

        processor.processInboundData();
        assertTrue(processor.pollMessage() instanceof Bitfield);
        assertTrue(processor.pollMessage() instanceof Have);
        /////////////////////////////////////////////////////////////

    }

    private void encodeToBuffer(Message message) {
        if (!protocol.encode(encodingContext, message, buffer)) {
            throw new IllegalStateException("Failed to put message into buffer");
        }
    }

    private void encodeToBuffer(Message message, ByteBuffer buffer) {
        if (!protocol.encode(encodingContext, message, buffer)) {
            throw new IllegalStateException("Failed to put message into buffer");
        }
    }
}
