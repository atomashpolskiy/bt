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

import java.util.Collections;
import java.util.List;

import static bt.TestUtil.assertExceptionWithMessage;
import static bt.data.ChunkDescriptorTestUtil.mockStorageUnits;

public class ReadWriteDataRange_ExceptionsTest {

    /**************************************************************************************************/

    @Test
    public void testDataRange_NoUnits() {
        List<StorageUnit> units = Collections.emptyList();
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, 1),
                "Empty list of units");
    }

    /**************************************************************************************************/

    @Test
    public void testDataRange_SingleUnit_NegativeOffset() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, -1, 1),
                "Invalid offset in first unit: -1, expected 0..255");
    }

    @Test
    public void testDataRange_SingleUnit_OffsetOverflow() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, len, 1),
                "Invalid offset in first unit: 256, expected 0..255");
    }

    @Test
    public void testDataRange_SingleUnit_NegativeLimit() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, -1),
                "Invalid limit in last unit: -1, expected 1..256");
    }

    @Test
    public void testDataRange_SingleUnit_LimitOverflow() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len + 1),
                "Invalid limit in last unit: 257, expected 1..256");
    }

    @Test
    public void testDataRange_SingleUnit_OffsetEqualToLimit() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 64, 64),
                "Offset is greater than limit in a single-unit range: 64 >= 64");
    }

    @Test
    public void testDataRange_SingleUnit_OffsetGreaterThanLimit() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 65, 64),
                "Offset is greater than limit in a single-unit range: 65 >= 64");
    }

    /**************************************************************************************************/

    @Test
    public void testDataRange_MultipleUnits_NegativeOffset() {
        long len1 = 256, len2 = 256;
        List<StorageUnit> units = mockStorageUnits(len1, len2);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, -1, len2),
                "Invalid offset in first unit: -1, expected 0..255");
    }

    @Test
    public void testDataRange_MultipleUnits_OffsetOverflow() {
        long len1 = 128, len2 = 256;
        List<StorageUnit> units = mockStorageUnits(len1, len2);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, len1, len2),
                "Invalid offset in first unit: 128, expected 0..127");
    }

    @Test
    public void testDataRange_MultipleUnits_NegativeLimit() {
        long len1 = 256, len2 = 256;
        List<StorageUnit> units = mockStorageUnits(len1, len2);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, -1),
                "Invalid limit in last unit: -1, expected 1..256");
    }

    @Test
    public void testDataRange_MultipleUnits_LimitOverflow() {
        long len1 = 256, len2 = 128;
        List<StorageUnit> units = mockStorageUnits(len1, len2);
        assertExceptionWithMessage(
                it -> new ReadWriteDataRange(units, 0, len2 + 1),
                "Invalid limit in last unit: 129, expected 1..128");
    }
}
