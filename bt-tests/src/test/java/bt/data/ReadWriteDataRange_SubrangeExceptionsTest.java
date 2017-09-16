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

package bt.data;

import org.junit.Test;

import java.util.List;

import static bt.TestUtil.assertExceptionWithMessage;
import static bt.data.ChunkDescriptorTestUtil.mockStorageUnits;

public class ReadWriteDataRange_SubrangeExceptionsTest {

    @Test
    public void testSubrange_ZeroLength() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len).getSubrange(0, 0),
                "Requested empty subrange, expected length of 1..256");
    }

    @Test
    public void testSubrange_ZeroLength_Implicit() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len).getSubrange(len),
                "Requested empty subrange, expected length of 1..256");
    }

    @Test
    public void testSubrange_NegativeLength() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len).getSubrange(0, -1),
                "Illegal arguments: offset (0), length (-1)");
    }

    @Test
    public void testSubrange_NegativeOffset() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len).getSubrange(-1),
                "Illegal arguments: offset (-1)");
    }

    @Test
    public void testSubrange_NegativeOffset_TwoArgsMethod() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len).getSubrange(-1, 1),
                "Illegal arguments: offset (-1), length (1)");
    }

    @Test
    public void testSubrange_OffsetOverflow() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len).getSubrange(len, 1),
                "Offset is too large: 256, expected 0..255");
    }
}
