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
import bt.net.buffer.BufferedData;
import bt.protocol.*;
import bt.protocol.handler.MessageHandler;
import bt.test.protocol.ProtocolTest;
import org.junit.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class InboundMessageProcessorTest {

    private EncodingContext encodingContext;
    private MessageHandler<Message> protocol;
    private ByteBuffer buffer;
    private IBufferedPieceRegistry bufferedPieceRegistry;
    private InboundMessageProcessor processor;

    public void setUp(int bufferSize) {
        this.buffer = ByteBuffer.allocate(bufferSize);
        InetPeer peer = InetPeer.build(InetAddress.getLoopbackAddress(), 9999);
        this.encodingContext = new EncodingContext(peer);
        this.protocol = ProtocolTest.forBittorrentProtocol().build().getProtocol();
        MessageDeserializer deserializer = new MessageDeserializer(peer, protocol);
        this.bufferedPieceRegistry = new BufferedPieceRegistry();
        this.processor = new InboundMessageProcessor(peer, buffer, deserializer, Collections.emptyList(), bufferedPieceRegistry);
    }

    @Test
    public void test_WithoutPieces_WithWrapAround() {
        setUp(100);
        testWithoutPieces();
    }

    @Test
    public void test_WithoutPieces_WithoutWrapAround() {
        setUp(1000);
        testWithoutPieces();
    }

    private void testWithoutPieces() {
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

    @Test
    public void test_WithPieces_WithWrapAround() {
        setUp(100);

        // Write several full messages with the last one being a block,
        // dispose immediately after receiving and checking
        encodeToBuffer(new Have(1));

        byte[] block1 = new byte[10];
        Arrays.fill(block1, (byte) 1);
        encodeToBuffer(new Piece(1, 1, block1.length, ProtocolTest.asBlockReader(block1)));

        processor.processInboundData();
        Have have1 = (Have) processor.pollMessage();
        assertNotNull(have1);
        assertEquals(1, have1.getPieceIndex());
        Piece piece1 = (Piece) processor.pollMessage();
        assertNotNull(piece1);
        assertEquals(1, piece1.getPieceIndex());
        assertEquals(1, piece1.getOffset());
        assertEquals(block1.length, piece1.getLength());
        BufferedData data1 = bufferedPieceRegistry.getBufferedPiece(piece1.getPieceIndex(), piece1.getOffset());
        assertNotNull(data1);
        byte[] data1Bytes = new byte[block1.length];
        data1.buffer().get(data1Bytes);
        assertArrayEquals(block1, data1Bytes);
        data1.dispose();
        /////////////////////////////////////////////////////////////

        // Write piece, then 2 full messages with one processing after 1st one,
        // delay piece disposal until 2nd message is consumed
        byte[] bitfield1 = new byte[20];
        Arrays.fill(bitfield1, (byte) 0xff);
        encodeToBuffer(new Bitfield(bitfield1));

        byte[] block2 = new byte[10];
        Arrays.fill(block2, (byte) 2);
        encodeToBuffer(new Piece(2, 2, block2.length, ProtocolTest.asBlockReader(block2)));

        processor.processInboundData();
        Bitfield bitfield1Message = (Bitfield) processor.pollMessage();
        assertNotNull(bitfield1Message);
        assertArrayEquals(bitfield1, bitfield1Message.getBitfield());
        Piece piece2 = (Piece) processor.pollMessage();
        assertNotNull(piece2);
        assertEquals(2, piece2.getPieceIndex());
        assertEquals(2, piece2.getOffset());
        assertEquals(block2.length, piece2.getLength());
//        BufferedData data2 = bufferedPieceRegistry.getBufferedPiece(piece2.getPieceIndex(), piece2.getOffset());
//        assertNotNull(data2);
//        byte[] data2Bytes = new byte[block2.length];
//        data2.buffer().get(data2Bytes);
//        assertArrayEquals(block2, data2Bytes);
        // do not dispose yet

        encodeToBuffer(new Have(2));

        processor.processInboundData();
        Have have2 = (Have) processor.pollMessage();
        assertNotNull(have2);
        assertEquals(2, have2.getPieceIndex());

        // Check if it's possible to overwrite undisposed data
        // (i.e. whether buffer's params are incorrect)
        assertEquals(60, buffer.remaining());

        byte[] bitfield2 = new byte[55]; // Bitfield is <length><id><...>, i.e. 4+1+X bytes
        Arrays.fill(bitfield2, (byte) 0xff);
        encodeToBuffer(new Bitfield(bitfield2));

        processor.processInboundData();

        Bitfield bitfield2Message = (Bitfield) processor.pollMessage();
        assertNotNull(bitfield2Message);
        assertArrayEquals(bitfield2, bitfield2Message.getBitfield());
        assertNull(processor.pollMessage());

        assertFalse(buffer.hasRemaining());

        // Just about time to dispose of second piece's data
        BufferedData data2 = bufferedPieceRegistry.getBufferedPiece(piece2.getPieceIndex(), piece2.getOffset());
        assertNotNull(data2);
        byte[] data2Bytes = new byte[block2.length];
        data2.buffer().get(data2Bytes);
        assertArrayEquals(block2, data2Bytes);
        data2.dispose();

        processor.processInboundData();

        assertEquals(buffer.capacity(), buffer.remaining());

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
