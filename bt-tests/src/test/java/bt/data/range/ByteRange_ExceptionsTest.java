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

import static bt.TestUtil.assertExceptionWithMessage;
import static bt.TestUtil.sequence;

public class ByteRange_ExceptionsTest {

    /*
     * Constructor tests
     */

    @Test
    public void testByteRange_FromByteArray_Empty() {
        assertExceptionWithMessage(it -> new ByteRange(new byte[0]), "Empty byte array");
    }

    @Test
    public void testByteRange_FromByteBuffer_Empty() {
        assertExceptionWithMessage(it -> new ByteRange(ByteBuffer.wrap(new byte[0])), "Empty buffer");
    }

    @Test
    public void testByteRange_FromByteArray_Offset_Negative() {
        assertExceptionWithMessage(it -> new ByteRange(new byte[1], -1, 1), "Invalid offset: -1, expected 0..0");
    }

    @Test
    public void testByteRange_FromByteArray_Offset_LargerThanAvailable() {
        assertExceptionWithMessage(it -> new ByteRange(new byte[1], 1, 1), "Invalid offset: 1, expected 0..0");
    }

    @Test
    public void testByteRange_FromByteArray_Offset_EqualToLimit() {
        assertExceptionWithMessage(it -> new ByteRange(new byte[1], 0, 0), "Offset is larger than or equal to limit (offset: 0, limit: 0)");
    }

    @Test
    public void testByteRange_FromByteArray_Limit_Negative() {
        assertExceptionWithMessage(it -> new ByteRange(new byte[1], 0, -1), "Invalid limit: -1, expected 1..1");
    }

    @Test
    public void testByteRange_FromByteArray_Limit_LargerThanAvailable() {
        assertExceptionWithMessage(it -> new ByteRange(new byte[1], 0, 2), "Invalid limit: 2, expected 1..1");
    }

    /*
     * Subrange creation tests
     */

    @Test
    public void testByteRange_Subrange_OffsetOnly_Negative() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(-1), "Invalid offset: -1, expected 0..9");
    }

    @Test
    public void testByteRange_Subrange_OffsetOnly_LargerThanAvailable() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(10), "Invalid offset: 10, expected 0..9");
    }

    @Test
    public void testByteRange_Subrange_OffsetOnly_Offset_IntegerOverflow() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(integerMaxValuePlusX(1)), "Offset is too large: 2147483648");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLength_Offset_Negative() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(-1, 1), "Invalid offset: -1, expected 0..9");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLength_Offset_LargerThanAvailable() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(10, 1), "Invalid offset: 10, expected 0..9");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLength_Offset_IntegerOverflow() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(integerMaxValuePlusX(1), integerMaxValuePlusX(2)),
                "Offset is too large: 2147483648");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLimit_Length_Negative() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(9, -1), "Requested negative length: -1");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLimit_Length_Zero() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(9, 0), "Requested empty subrange, expected length of 1..1");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLimit_Length_InsufficientData() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(9, 2), "Insufficient data: requested 2 bytes, expected 1..1");
    }

    @Test
    public void testByteRange_Subrange_OffsetAndLength_Length_IntegerOverflow() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> range.getSubrange(0, integerMaxValuePlusX(1)),
                "Insufficient data: requested 2147483648 bytes, expected 1..10");
    }

    /*
     * Modification tests
     */

    @Test
    public void testByteRange_Modification_PutBytes_TooLarge() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> {range.putBytes(new byte[11]);return null;},
                "Data does not fit in this range (expected max 10 bytes, actual: 11)");
    }

    @Test
    public void testByteRange_Modification_Subrange_PutBytes_TooLarge() {
        ByteRange range = new ByteRange(sequence(10));
        assertExceptionWithMessage(it -> {range.getSubrange(5).putBytes(new byte[6]);return null;},
                "Data does not fit in this range (expected max 5 bytes, actual: 6)");
    }

    @Test
    public void testByteRange_Modification_PutBytes_ZeroLength_NoException() {
        ByteRange range = new ByteRange(sequence(10));
        range.putBytes(new byte[0]);
    }

    private static long integerMaxValuePlusX(int x) {
        return Long.valueOf(Integer.MAX_VALUE) + x;
    }
}
