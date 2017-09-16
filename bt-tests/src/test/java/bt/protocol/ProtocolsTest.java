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

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ProtocolsTest {

    private static final byte[] bytes = new byte[]{0x00, 0x0F, (byte) 0xF0, (byte) 0xFF};

    @Test
    public void test_toHex() {
        assertEquals("000ff0ff", Protocols.toHex(bytes));
    }

    @Test
    public void test_fromHex_LowerCase() {
        String s = "000ff0ff";
        assertArrayEquals(bytes, Protocols.fromHex(s));
    }

    @Test
    public void test_fromHex_UpperCase() {
        String s = "000FF0FF";
        assertArrayEquals(bytes, Protocols.fromHex(s));
    }

    @Test
    public void test_fromHex_MixedCase() {
        String s = "000fF0Ff";
        assertArrayEquals(bytes, Protocols.fromHex(s));
    }
}
