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

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReadByBlockChannelTest {

    @Test
    public void testChannel_NoPartialReads() throws IOException {
        int[] limits = new int[]        {100, 200, 300};
        int[] expectedReads = new int[] {100, 100, 100};
        testChannel(limits, expectedReads);
    }

    @Test
    public void testChannel_PartialReads() throws IOException {
        int[] limits = new int[]        {0, 75, 100, 150, 250, 250, 300};
        int[] expectedReads = new int[] {0, 75,  25,  50,  50,  50,  50};
        testChannel(limits, expectedReads);
    }

    private void testChannel(int[] limits, int[] expectedReads) throws IOException {
        if (limits.length != expectedReads.length) {
            throw new IllegalArgumentException("Lengths do not match");
        }

        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[100];

        Arrays.fill(part1, (byte) 1);
        Arrays.fill(part2, (byte) 2);

        List<byte[]> data = new ArrayList<>(Arrays.asList(part0, part1, part2));
        ReadByBlockChannel channel = new ReadByBlockChannel(data);

        int lenTotal = part0.length + part1.length + part2.length;
        ByteBuffer buf = ByteBuffer.allocate(lenTotal);
        int readTotal = 0;
        int read;
        for (int i = 0; i < limits.length; i++) {
            int limit = limits[i];
            int expectedRead = expectedReads[i];
            buf.limit(limit);
            read = channel.read(buf);
            assertEquals(String.format("Read #%s: expected (%s), actual (%s)", i, expectedRead, read), expectedRead, read);
            if (read > 0) {
                readTotal += read;
            }
        }

        // all data has been read
        assertEquals(-1, channel.read(buf));
        assertEquals(lenTotal, readTotal);
        assertFalse(buf.hasRemaining());

        buf.flip();
        byte[] expected = new byte[lenTotal];
        System.arraycopy(part0, 0, expected, 0, part0.length);
        System.arraycopy(part1, 0, expected, part0.length, part1.length);
        System.arraycopy(part2, 0, expected, part0.length + part1.length, part2.length);

        byte[] actual = new byte[lenTotal];
        buf.get(actual);

        assertArrayEquals(expected, actual);
    }
}
