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

public class Protocol_InsufficientDataTest {
    
    private static final ProtocolTest TEST = ProtocolTest.forBittorrentProtocol().build();

    private byte[] HANDSHAKE_INSUFFICIENT_DATA = new byte[]{
            19,/*--protocol-name*/66,105,116,84,111,114,114,101,110,116,32,112,114,111,116,111,99,111,108,
            /*--reserved*/0,0,0,0,0,0,0,0,/*--info-hash*/0,1,2,3,4,5,6,7,8,9,10,11/*--pending-data...*/};

    @Test
    public void testProtocol_Handshake_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(
                Handshake.class, HANDSHAKE_INSUFFICIENT_DATA);
    }

    private byte[] INSUFFICIENT_DATA_1 = new byte[]{};
    private byte[] INSUFFICIENT_DATA_2 = new byte[]{0};
    private byte[] INSUFFICIENT_DATA_3 = new byte[]{0,0};
    private byte[] INSUFFICIENT_DATA_4 = new byte[]{0,0,0};
    private byte[] INSUFFICIENT_DATA_5 = new byte[]{0,0,0,1};

    @Test
    public void testProtocol_UnknownType_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(INSUFFICIENT_DATA_1);
        TEST.assertInsufficientDataAndNothingConsumed(INSUFFICIENT_DATA_2);
        TEST.assertInsufficientDataAndNothingConsumed(INSUFFICIENT_DATA_3);
        TEST.assertInsufficientDataAndNothingConsumed(INSUFFICIENT_DATA_4);
        TEST.assertInsufficientDataAndNothingConsumed(INSUFFICIENT_DATA_5);
    }

    private byte[] HAVE_INSUFFICIENT_DATA = new byte[]{0,0,0,5,4,/*--piece-index*/0,0,16/*--pending-data...*/};

    private byte[] BITFIELD_INSUFFICIENT_DATA = new byte[]{0,0,0,3,5,/*--bitfield*/-1/*--pending-data...*/};

    private byte[] REQUEST_INSUFFICIENT_DATA = new byte[]{
            0,0,0,13,6,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0/*--pending-data...*/};

    private byte[] PIECE_INSUFFICIENT_DATA = new byte[]{
            0,0,0,17,7,/*--piece-index*/0,0,0,1,/*--offset*/0,1,0,0,/*--block*/1,0,1,0/*--pending-data...*/};

    private byte[] CANCEL_INSUFFICIENT_DATA = new byte[]{
            0,0,0,13,8,/*--piece-index*/0,0,0,1,/*--offset*/0/*--pending-data...*/};

    @Test
    public void testProtocol_Have_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(
                Have.class, HAVE_INSUFFICIENT_DATA);
    }

    @Test
    public void testProtocol_Bitfield_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(
                Bitfield.class, BITFIELD_INSUFFICIENT_DATA);
    }

    @Test
    public void testProtocol_Request_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(
                Request.class, REQUEST_INSUFFICIENT_DATA);
    }

    @Test
    public void testProtocol_Piece_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(
                Piece.class, PIECE_INSUFFICIENT_DATA);
    }

    @Test
    public void testProtocol_Cancel_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(
                Cancel.class, CANCEL_INSUFFICIENT_DATA);
    }
}
