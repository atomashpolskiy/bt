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

import bt.metainfo.TorrentId;
import bt.net.PeerId;
import bt.test.protocol.ProtocolTest;
import org.junit.Test;

public class Protocol_CorrectDataTest {
    
    private static final ProtocolTest TEST = ProtocolTest.forBittorrentProtocol().build();

    //--- handshake comes first ---//

    private byte[] HANDSHAKE = new byte[]{
            19,/*--protocol-name*/66,105,116,84,111,114,114,101,110,116,32,112,114,111,116,111,99,111,108,
            /*--reserved*/0,0,0,0,0,0,0,0,/*--info-hash*/0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,
            /**--peer-id*/20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39};

    private byte[] HANDSHAKE_TRAILING_DATA = new byte[]{
            19,/*--protocol-name*/66,105,116,84,111,114,114,101,110,116,32,112,114,111,116,111,99,111,108,
            /*--reserved*/0,0,0,0,0,0,0,0,/*--info-hash*/0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,
            /**--peer-id*/20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,
            /**--trailing-data*/-30,2,0,0,1,127};

    @Test
    public void testProtocol_Handshake_ExactBytes() throws Exception {

        Handshake expected = new Handshake(
                new byte[8],
                TorrentId.fromBytes(new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19}),
                PeerId.fromBytes(new byte[]{20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39})
        );

        TEST.assertDecoded(HANDSHAKE.length, expected, HANDSHAKE);
    }

    @Test
    public void testProtocol_Handshake_TrailingBytes() throws Exception {

        Handshake expected = new Handshake(
                new byte[8],
                TorrentId.fromBytes(new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19}),
                PeerId.fromBytes(new byte[]{20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39})
        );

        TEST.assertDecoded(HANDSHAKE.length, expected, HANDSHAKE_TRAILING_DATA);
    }

    //--- fixed-size messages without payload ---//
    
    private byte[] KEEPALIVE = new byte[]{0,0,0,0};
    private byte[] KEEPALIVE_TRAILING_DATA = new byte[]{0,0,0,0,/*--trailing-part*/9,1,127};

    private byte[] CHOKE = new byte[]{0,0,0,1,0};
    private byte[] CHOKE_TRAILING_DATA = new byte[]{0,0,0,1,0,/*--trailing-part*/-1,0,21};

    private byte[] UNCHOKE = new byte[]{0,0,0,1,1};
    private byte[] UNCHOKE_TRAILING_DATA = new byte[]{0,0,0,1,1,/*--trailing-part*/-128,0,1};

    private byte[] INTERESTED = new byte[]{0,0,0,1,2};
    private byte[] INTERESTED_TRAILING_DATA = new byte[]{0,0,0,1,2,/*--trailing-part*/1,101,20,0};

    private byte[] NOT_INTERESTED = new byte[]{0,0,0,1,3};
    private byte[] NOT_INTERESTED_TRAILING_DATA = new byte[]{0,0,0,1,3,/*--trailing-part*/1,-100,1,4};

    @Test
    public void testProtocol_KeepAlive_ExactBytes() throws Exception {
        TEST.assertDecoded(KEEPALIVE.length, KeepAlive.instance(), KEEPALIVE);
    }

    @Test
    public void testProtocol_KeepAlive_TrailingBytes() throws Exception {
        TEST.assertDecoded(KEEPALIVE.length, KeepAlive.instance(), KEEPALIVE_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Choke_ExactBytes() throws Exception {
        TEST.assertDecoded(CHOKE.length, Choke.instance(), CHOKE);
    }

    @Test
    public void testProtocol_Choke_TrailingBytes() throws Exception {
        TEST.assertDecoded(CHOKE.length, Choke.instance(), CHOKE_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Unchoke_ExactBytes() throws Exception {
        TEST.assertDecoded(UNCHOKE.length, Unchoke.instance(), UNCHOKE);
    }

    @Test
    public void testProtocol_Unchoke_TrailingBytes() throws Exception {
        TEST.assertDecoded(UNCHOKE.length, Unchoke.instance(), UNCHOKE_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Interested_ExactBytes() throws Exception {
        TEST.assertDecoded(INTERESTED.length, Interested.instance(), INTERESTED);
    }

    @Test
    public void testProtocol_Interested_TrailingBytes() throws Exception {
        TEST.assertDecoded(INTERESTED.length, Interested.instance(), INTERESTED_TRAILING_DATA);
    }

    @Test
    public void testProtocol_NotInterested_ExactBytes() throws Exception {
        TEST.assertDecoded(NOT_INTERESTED.length, NotInterested.instance(), NOT_INTERESTED);
    }

    @Test
    public void testProtocol_NotInterested_TrailingBytes() throws Exception {
        TEST.assertDecoded(NOT_INTERESTED.length, NotInterested.instance(), NOT_INTERESTED_TRAILING_DATA);
    }

    //--- fixed- and variable-size messages with payload ---//

    private byte[] HAVE = new byte[]{0,0,0,5,4,/*--piece-index*/0,0,16,127};
    private byte[] HAVE_TRAILING_DATA = new byte[]{0,0,0,5,4,/*--piece-index*/0,0,16,127,/*--trailing-data*/29,-3,0};

    private byte[] BITFIELD = new byte[]{0,0,0,3,5,/*--bitfield*/-1,-1};
    private byte[] BITFIELD_TRAILING_DATA = new byte[]{0,0,0,3,5,/*--bitfield*/-1,-1,/*--trailing-data*/119,23,-30};

    private byte[] REQUEST = new byte[]{0,0,0,13,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};
    private byte[] REQUEST_TRAILING_DATA = new byte[]{
            0,0,0,13,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0,/*--trailing-data*/126,-78};

    private byte[] PIECE = new byte[]{0,0,0,17,7,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--block*/1,0,1,0,1,0,1,0};
    private byte[] PIECE_TRAILING_DATA = new byte[]{
            0,0,0,17,7,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--block*/1,0,1,0,1,0,1,0,/*--trailing-data*/-1,0};

    private byte[] CANCEL = new byte[]{0,0,0,13,8,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0};
    private byte[] CANCEL_TRAILING_DATA = new byte[]{
            0,0,0,13,8,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--length*/0,0,64,0,/*--trailing-data*/126,-78};

    @Test
    public void testProtocol_Have_ExactBytes() throws Exception {

        Have expected = new Have(16 * (2 << 7) + 127);
        TEST.assertDecoded(HAVE.length, expected, HAVE);
    }

    @Test
    public void testProtocol_Have_TrailingBytes() throws Exception {

        Have expected = new Have(16 * (2 << 7) + 127);
        TEST.assertDecoded(HAVE.length, expected, HAVE_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Bitfield_ExactBytes() throws Exception {

        Bitfield expected = new Bitfield(new byte[]{-1,-1});
        TEST.assertDecoded(BITFIELD.length, expected, BITFIELD);
    }

    @Test
    public void testProtocol_Bitfield_TrailingBytes() throws Exception {

        Bitfield expected = new Bitfield(new byte[]{-1,-1});
        TEST.assertDecoded(BITFIELD.length, expected, BITFIELD_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Request_ExactBytes() throws Exception {

        Request expected = new Request(1, (2 << 15), 64 * (2 << 7));
        TEST.assertDecoded(REQUEST.length, expected, REQUEST);
    }

    @Test
    public void testProtocol_Request_TrailingBytes() throws Exception {

        Request expected = new Request(1, (2 << 15), 64 * (2 << 7));
        TEST.assertDecoded(REQUEST.length, expected, REQUEST_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Piece_ExactBytes() throws Exception {

        Piece expected = new Piece(1, (2 << 15), new byte[]{1,0,1,0,1,0,1,0});
        TEST.assertDecoded(PIECE.length, expected, PIECE);
    }

    @Test
    public void testProtocol_Piece_TrailingBytes() throws Exception {

        Piece expected = new Piece(1, (2 << 15), new byte[]{1,0,1,0,1,0,1,0});
        TEST.assertDecoded(PIECE.length, expected, PIECE_TRAILING_DATA);
    }

    @Test
    public void testProtocol_Cancel_ExactBytes() throws Exception {

        Cancel expected = new Cancel(1, (2 << 15), 64 * (2 << 7));
        TEST.assertDecoded(CANCEL.length, expected, CANCEL);
    }

    @Test
    public void testProtocol_Cancel_TrailingBytes() throws Exception {

        Cancel expected = new Cancel(1, (2 << 15), 64 * (2 << 7));
        TEST.assertDecoded(CANCEL.length, expected, CANCEL_TRAILING_DATA);
    }
}
