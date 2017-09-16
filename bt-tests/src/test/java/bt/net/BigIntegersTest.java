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

package bt.net;

import bt.net.BigIntegers;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BigIntegersTest {
    
    // 768-bit prime
    private static final BigInteger P = new BigInteger(
                                    "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                                    "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                                    "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                                    "E485B576625E7EC6F44C42E9A63A36210000000000090563", 16);

    private static final int[] Pbytes = new int[] {
            0xFF,  0xFF,  0xFF,  0xFF,  0xFF,  0xFF,  0xFF,  0xFF,  0xC9,  0x0F,  0xDA,  0xA2,
            0x21,  0x68,  0xC2,  0x34,  0xC4,  0xC6,  0x62,  0x8B,  0x80,  0xDC,  0x1C,  0xD1,
            0x29,  0x02,  0x4E,  0x08,  0x8A,  0x67,  0xCC,  0x74,  0x02,  0x0B,  0xBE,  0xA6,
            0x3B,  0x13,  0x9B,  0x22,  0x51,  0x4A,  0x08,  0x79,  0x8E,  0x34,  0x04,  0xDD,
            0xEF,  0x95,  0x19,  0xB3,  0xCD,  0x3A,  0x43,  0x1B,  0x30,  0x2B,  0x0A,  0x6D,
            0xF2,  0x5F,  0x14,  0x37,  0x4F,  0xE1,  0x35,  0x6D,  0x6D,  0x51,  0xC2,  0x45,
            0xE4,  0x85,  0xB5,  0x76,  0x62,  0x5E,  0x7E,  0xC6,  0xF4,  0x4C,  0x42,  0xE9,
            0xA6,  0x3A,  0x36,  0x21,  0x00,  0x00,  0x00,  0x00,  0x00,  0x09,  0x05,  0x63,
    };

    @Test
    public void testBigIntegers() {
        assertSameAfterConversion(BigInteger.ZERO);
        assertSameAfterConversion(BigInteger.ONE);
        assertSameAfterConversion(BigInteger.valueOf(Long.MAX_VALUE));
        assertSameAfterConversion(P);

        assertArrayEquals(
                BigIntegers.encodeUnsigned(BigInteger.valueOf(255), 4),
                BigIntegers.encodeUnsigned(BigInteger.valueOf(-1), 4));

        assertEquals(BigInteger.ZERO, BigIntegers.decodeUnsigned(ByteBuffer.wrap(new byte[20]), 20));
        assertEquals(BigInteger.valueOf(255), BigIntegers.decodeUnsigned(ByteBuffer.wrap(new byte[]{0,0,0,-1}), 4));

        assertEquals(96, Pbytes.length);
        assertEquals(P, BigIntegers.decodeUnsigned(ByteBuffer.wrap(toByteArray(Pbytes)), 96));
        assertArrayEquals(toByteArray(Pbytes), BigIntegers.encodeUnsigned(P, Pbytes.length));
    }

    private static void assertSameAfterConversion(BigInteger i) {
        byte[] bytes = BigIntegers.encodeUnsigned(i, 96);
        BigInteger j = BigIntegers.decodeUnsigned(ByteBuffer.wrap(bytes), 96);
        assertEquals(i, j);
    }
    
    private static byte[] toByteArray(int[] ints) {
        int len = ints.length;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) ints[i];
        }
        return bytes;
    }
}
