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

import bt.protocol.handler.PortMessageHandler;
import bt.test.protocol.ProtocolTest;
import org.junit.Test;

public class PortMessageHandlerTest {
    
    private static final ProtocolTest TEST = ProtocolTest.forBittorrentProtocol()
            .extraMessageHandler(9, new PortMessageHandler())
            .matcher(Port.class, new PortMatcher())
            .build();

    private byte[] PORT = new byte[]{0,0,0,3,9,/*--port*/24,0};
    private byte[] PORT_TRAILING_DATA = new byte[]{0,0,0,3,9,/*--port*/24,0,/*--trailing-data*/29,-3,0};

    @Test
    public void testProtocol_Port_ExactBytes() throws Exception {

        Port expected = new Port(24 * (2 << 7));
        TEST.assertDecoded(PORT.length, expected, PORT);
    }

    @Test
    public void testProtocol_Port_TrailingBytes() throws Exception {

        Port expected = new Port(24 * (2 << 7));
        TEST.assertDecoded(PORT.length, expected, PORT_TRAILING_DATA);
    }

    private byte[] PORT_INSUFFICIENT_DATA = new byte[]{0,0,0,3,9,/*--port*/24/*--pending-data...*/};

    @Test
    public void testProtocol_Port_InsufficientBytes() throws Exception {
        TEST.assertInsufficientDataAndNothingConsumed(Port.class, PORT_INSUFFICIENT_DATA);
    }
}
