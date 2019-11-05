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

import bt.net.InetPeer;
import bt.net.ReadByBlockChannel;
import bt.net.buffer.BufferedData;
import bt.protocol.EncodingContext;
import bt.protocol.Message;
import bt.protocol.Piece;
import bt.protocol.handler.MessageHandler;
import bt.test.protocol.ProtocolTest;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class InboundMessageProcessor_BufferRotationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(InboundMessageProcessor_BufferRotationTest.class);

    private static final int BUFFER_LENGTH = 3697;

    private EncodingContext encodingContext;
    private MessageHandler<Message> protocol;
    private ByteBuffer buffer;
    private IBufferedPieceRegistry bufferedPieceRegistry;
    private InboundMessageProcessor processor;

    @Before
    public void setUp() {
        this.buffer = ByteBuffer.allocate(BUFFER_LENGTH);
        InetPeer peer = InetPeer.build(InetAddress.getLoopbackAddress(), 9999);
        this.encodingContext = new EncodingContext(peer);
        this.protocol = ProtocolTest.forBittorrentProtocol().build().getProtocol();
        MessageDeserializer deserializer = new MessageDeserializer(peer, protocol);
        this.bufferedPieceRegistry = new BufferedPieceRegistry();
        this.processor = new InboundMessageProcessor(peer, buffer, deserializer, Collections.emptyList(), bufferedPieceRegistry);
    }

    @Test
    public void test() throws IOException {
        // length of a Piece message is 9 bytes (prefix) + block length
        int BLOCK_LENGTH = 87;
        int PIECE_MESSAGE_LENGTH = BLOCK_LENGTH + 13;
        int MAX_FULL_MESSAGES_IN_BUFFER = BUFFER_LENGTH / PIECE_MESSAGE_LENGTH; // not including partial message

        byte[][] blocks = new byte[10][];
        for (int i = 0; i < 10; i++) {
            blocks[i] = new byte[BLOCK_LENGTH];
            Arrays.fill(blocks[i], (byte) i);
        }

        List<byte[]> encodedPieces = new ArrayList<>(MAX_FULL_MESSAGES_IN_BUFFER + 1);
        {
            ByteBuffer buf = ByteBuffer.allocate(PIECE_MESSAGE_LENGTH);
            for (int i = 0; i < MAX_FULL_MESSAGES_IN_BUFFER; i++) {
                byte[] block = blocks[i % 10];
                if (!protocol.encode(encodingContext,
                        new Piece(1, i, BLOCK_LENGTH, ProtocolTest.asBlockReader(block)),
                        buf)) {
                    throw new IllegalStateException("Failed to encode block " + i);
                }
                buf.flip();
                if (buf.remaining() != PIECE_MESSAGE_LENGTH) {
                    throw new IllegalStateException("Invalid piece message size");
                }
                byte[] encodedPiece = new byte[PIECE_MESSAGE_LENGTH];
                buf.get(encodedPiece);
                encodedPieces.add(encodedPiece);
                buf.clear();
            }
        }

        int j = 0;
        ReadableByteChannel ch = new ReadByBlockChannel(encodedPieces);
        while (j < 1_000_000) {
            int lastRead;
            do {
                lastRead = ch.read(buffer);
                if (lastRead == -1) {
                    ch = new ReadByBlockChannel(encodedPieces);
                }
            } while (buffer.hasRemaining() && lastRead != 0);

            processor.processInboundData();

            Message message;
            while ((message = processor.pollMessage()) != null) {
                byte[] encodedBlock = blocks[(j % encodedPieces.size()) % 10];
                Piece piece = (Piece) message;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Inspecting next decoded piece: {}", piece);
                }
                BufferedData decodedData = bufferedPieceRegistry.getBufferedPiece(piece.getPieceIndex(), piece.getOffset());
                assertEquals(encodedBlock.length, decodedData.length());
                byte[] decodedBlock = new byte[decodedData.buffer().remaining()];
                decodedData.buffer().get(decodedBlock);
                assertArrayEquals(
                        "\nExpect: "+Arrays.toString(encodedBlock)+"\nActual: "+Arrays.toString(decodedBlock),
                        encodedBlock, decodedBlock);
                decodedData.dispose();
                j++;
            }
            processor.processInboundData(); // wrap-around
        }

    }
}
