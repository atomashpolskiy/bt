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

package bt.data.range;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static bt.TestUtil.sequence;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteRangeTest {

    /*
     * Constructor tests
     */

    @Test
    public void testByteRange_FromByteArray() {
        byte[] bytes = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes);
        assertEquals(10, range.length());
        assertArrayEquals(bytes, range.getBytes());
    }

    @Test
    public void testByteRange_FromByteArray_Subrange_Full() {
        byte[] bytes = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes, 0, bytes.length);
        assertEquals(10, range.length());
        assertArrayEquals(bytes, range.getBytes());
    }

    @Test
    public void testByteRange_FromByteArray_Subrange_Tail() {
        byte[] bytes = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes, 1, bytes.length);
        assertEquals(9, range.length());
        assertArrayEquals(Arrays.copyOfRange(bytes, 1, bytes.length), range.getBytes());
    }

    @Test
    public void testByteRange_FromByteArray_Subrange_Head() {
        byte[] bytes = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes, 0, bytes.length - 1);
        assertEquals(9, range.length());
        assertArrayEquals(Arrays.copyOfRange(bytes, 0, bytes.length - 1), range.getBytes());
    }

    @Test
    public void testByteRange_FromByteArray_Subrange_Middle() {
        byte[] bytes = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes, 1, bytes.length - 1);
        assertEquals(8, range.length());
        assertArrayEquals(Arrays.copyOfRange(bytes, 1, bytes.length - 1), range.getBytes());
    }

    @Test
    public void testByteRange_FromByteBuffer() {
        byte[] bytes = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(ByteBuffer.wrap(bytes));
        assertEquals(10, range.length());
        assertArrayEquals(bytes, range.getBytes());
    }

    /*
     * Subrange equal to original
     */

    @Test
    public void testByteRange_SubrangeEqualToOriginal_OffsetOnly() {
        ByteRange range = new ByteRange(sequence(10));
        assertEquals(range, range.getSubrange(0));
    }

    @Test
    public void testByteRange_SubrangeEqualToOriginal_OffsetAndLength() {
        ByteRange range = new ByteRange(sequence(10));
        assertEquals(range, range.getSubrange(0, 10));
    }

    /*
     * Modification tests
     */

    @Test
    public void testByteRange_Modification() {
        byte[] bytes1 = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes1);

        /*full replace*/
        byte[] bytes2 = new byte[]{9,8,7,6,5,4,3,2,1,0};
        range.putBytes(bytes2);
        assertEquals(10, range.length());
        assertArrayEquals(bytes2, range.getBytes());

        /*update head*/
        byte[] bytes3 = new byte[]{20,21,22,23,24};
        range.putBytes(bytes3);
        assertEquals(10, range.length());
        assertArrayEquals(new byte[]{/*bytes3*/20,21,22,23,24,/*bytes2*/4,3,2,1,0}, range.getBytes());
    }

    @Test
    public void testByteRange_Modification_SubrangeOverlaps() {
        byte[] bytes = new byte[]{1,2,3,4,5,6,7,8,9,10};
        ByteRange range = new ByteRange(bytes);

        /*get first byte*/
        ByteRange firstByte = range.getSubrange(0,1);
        assertArrayEquals(new byte[]{1}, firstByte.getBytes());
        /*get last byte*/
        ByteRange lastByte = range.getSubrange(9,1);
        assertArrayEquals(new byte[]{10}, lastByte.getBytes());

        ByteRange head = range.getSubrange(0, 6);
        assertArrayEquals(new byte[]{1,2,3,4,5,6}, head.getBytes());
        ByteRange tail = range.getSubrange(4, 6);
        assertArrayEquals(new byte[]{5,6,7,8,9,10}, tail.getBytes());

        /*modify first byte*/
        firstByte.putBytes(new byte[]{77});
        assertArrayEquals(new byte[]{77,2,3,4,5,6}, head.getBytes());

        /*modify last byte*/
        lastByte.putBytes(new byte[]{88});
        assertArrayEquals(new byte[]{5,6,7,8,9,88}, tail.getBytes());

        /*modify head and tail overlap*/
        head.putBytes(new byte[]{91,92,93,94,95});
        assertArrayEquals(new byte[]{95,6,7,8,9,88}, tail.getBytes());

        assertArrayEquals(new byte[]{91,92,93,94,95,6,7,8,9,88}, range.getBytes());
    }
}
