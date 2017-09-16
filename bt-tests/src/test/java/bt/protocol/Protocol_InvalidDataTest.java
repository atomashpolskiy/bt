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

package bt.protocol;

import bt.test.protocol.ProtocolTest;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class Protocol_InvalidDataTest {
    
    private static final ProtocolTest TEST = ProtocolTest.forBittorrentProtocol().build();

    private byte[] HANDSHAKE_INVALID_DATA = new byte[]{
            19,/*--protocol-name (wrong-first-byte)*/-1,105,116,84,111,114,114,101,110,116,32,112,114,111,116,111,99,111,108,
            /*--reserved*/0,0,0,0,0,0,0,0,/*--info-hash*/0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,
            /**--peer-id*/20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39};

    @Test
    public void testProtocol_Handshake_Invalid() throws Exception {

        String expectedMessage = "Unexpected protocol name (decoded with ASCII): " +
                new String(new byte[]{-1,105,116,84,111,114,114,101,110,116,32,112,114,111,116,111,99,111,108},
                        Charset.forName("ASCII"));

        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(HANDSHAKE_INVALID_DATA));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    private byte[] HAVE_INVALID_DATA_NEGATIVE_PIECE_INDEX = new byte[]{0,0,0,5,4,/*--piece-index*/-1,-1,-1,-1};

    @Test
    public void testProtocol_Have_InvalidData_NegativePieceIndex() throws Exception {

        String expectedMessage = "Illegal argument: piece index (-1)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(HAVE_INVALID_DATA_NEGATIVE_PIECE_INDEX));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    private byte[] REQUEST_INVALID_DATA_NEGATIVE_PIECE_INDEX = new byte[]{
            0,0,0,13,6,/*--piece-index*/-1,-1,-1,-1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};

    private byte[] REQUEST_INVALID_DATA_NEGATIVE_OFFSET = new byte[]{
            0,0,0,13,6,/*--piece-index*/0,0,0,1,/*--offset*/-1,-1,-1,-1,/*--length*/0,0,64,0};

    private byte[] REQUEST_INVALID_DATA_NEGATIVE_LENGTH = new byte[]{
            0,0,0,13,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/-1,-1,-1,-1};

    private byte[] REQUEST_INVALID_DATA_ZERO_LENGTH = new byte[]{
            0,0,0,13,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,0,0};

    @Test
    public void testProtocol_Request_InvalidData_NegativePieceIndex() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (-1), offset (65536), length (16384)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(REQUEST_INVALID_DATA_NEGATIVE_PIECE_INDEX));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Request_InvalidData_NegativeOffset() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (1), offset (-1), length (16384)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(REQUEST_INVALID_DATA_NEGATIVE_OFFSET));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Request_InvalidData_NegativeLength() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (1), offset (65536), length (-1)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(REQUEST_INVALID_DATA_NEGATIVE_LENGTH));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Request_InvalidData_ZeroLength() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (1), offset (65536), length (0)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(REQUEST_INVALID_DATA_ZERO_LENGTH));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    private byte[] PIECE_INVALID_DATA_NEGATIVE_PIECE_INDEX = new byte[]{
            0,0,0,17,7,/*--piece-index*/-1,-1,-1,-1,/*--offset*/0,1,0,0,/*--block*/1,0,1,0,1,0,1,0};

    private byte[] PIECE_INVALID_DATA_NEGATIVE_OFFSET = new byte[]{
            0,0,0,17,7,/*--piece-index*/0,0,0,1,/*--offset*/-1,-1,-1,-1,/*--block*/1,0,1,0,1,0,1,0};

    private byte[] PIECE_INVALID_DATA_EMPTY_BLOCK = new byte[]{
            0,0,0,9,7,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0/*--block-missing*/};

    @Test
    public void testProtocol_Piece_InvalidData_NegativeIndex() throws Exception {

        String expectedMessage = "Invalid arguments: piece index (-1), offset (65536), block length (8)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(PIECE_INVALID_DATA_NEGATIVE_PIECE_INDEX));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Piece_InvalidData_NegativeOffset() throws Exception {

        String expectedMessage = "Invalid arguments: piece index (1), offset (-1), block length (8)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(PIECE_INVALID_DATA_NEGATIVE_OFFSET));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Piece_InvalidData_EmptyBlock() throws Exception {

        String expectedMessage = "Invalid arguments: piece index (1), offset (65536), block length (0)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(PIECE_INVALID_DATA_EMPTY_BLOCK));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    private byte[] CANCEL_INVALID_DATA_NEGATIVE_PIECE_INDEX = new byte[]{
            0,0,0,13,8,/*--piece-index*/-1,-1,-1,-1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};

    private byte[] CANCEL_INVALID_DATA_NEGATIVE_OFFSET = new byte[]{
            0,0,0,13,8,/*--piece-index*/0,0,0,1,/*--offset*/-1,-1,-1,-1,/*--length*/0,0,64,0};

    private byte[] CANCEL_INVALID_DATA_NEGATIVE_LENGTH = new byte[]{
            0,0,0,13,8,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/-1,-1,-1,-1};

    private byte[] CANCEL_INVALID_DATA_ZERO_LENGTH = new byte[]{
            0,0,0,13,8,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,0,0};

    @Test
    public void testProtocol_Cancel_InvalidData_NegativePieceIndex() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (-1), offset (65536), length (16384)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(CANCEL_INVALID_DATA_NEGATIVE_PIECE_INDEX));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Cancel_InvalidData_NegativeOffset() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (1), offset (-1), length (16384)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(CANCEL_INVALID_DATA_NEGATIVE_OFFSET));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Cancel_InvalidData_NegativeLength() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (1), offset (65536), length (-1)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(CANCEL_INVALID_DATA_NEGATIVE_LENGTH));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testProtocol_Cancel_InvalidData_ZeroLength() throws Exception {

        String expectedMessage = "Illegal arguments: piece index (1), offset (65536), length (0)";
        InvalidMessageException e = null;
        try {
            TEST.getProtocol().decode(TEST.createDecodingContext(), ByteBuffer.wrap(CANCEL_INVALID_DATA_ZERO_LENGTH));
        } catch (InvalidMessageException e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals(expectedMessage, e.getMessage());
    }
}
