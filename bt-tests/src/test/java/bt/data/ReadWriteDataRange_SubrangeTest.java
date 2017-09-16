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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static bt.data.ChunkDescriptorTestUtil.mockStorageUnits;
import static bt.data.ReadWriteDataRangeTest.assertHasUnits;
import static org.junit.Assert.assertEquals;

public class ReadWriteDataRange_SubrangeTest {

    /**************************************************************************************************/

    @Test
    public void testSubrange_SingleUnit_Full() {
        long len = 256;
        List<StorageUnit> units = mockStorageUnits(len);
        DataRange range = new ReadWriteDataRange(units, 0, len).getSubrange(0);
        assertEquals(len, range.length());

        List<UnitAccess> expectedUnits = Collections.singletonList(new UnitAccess(units.get(0), 0, len));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_SingleUnit_WithOffset() {
        long len = 256;
        long off = 16;
        List<StorageUnit> units = mockStorageUnits(len);
        DataRange range = new ReadWriteDataRange(units, 0, len).getSubrange(16);
        assertEquals(len - off, range.length());

        List<UnitAccess> expectedUnits = Collections.singletonList(new UnitAccess(units.get(0), off, len));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_SingleUnit_WithLength() {
        long len = 256;
        long lim = 192;
        List<StorageUnit> units = mockStorageUnits(len);
        DataRange range = new ReadWriteDataRange(units, 0, len).getSubrange(0, lim);
        assertEquals(lim, range.length());

        List<UnitAccess> expectedUnits = Collections.singletonList(new UnitAccess(units.get(0), 0, lim));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_SingleUnit_WithOffsetAndLength() {
        long len = 256;
        long off = 16;
        long lim = 192;
        List<StorageUnit> units = mockStorageUnits(len);
        DataRange range = new ReadWriteDataRange(units, 0, len).getSubrange(off, lim - off);
        assertEquals(lim - off, range.length());

        List<UnitAccess> expectedUnits = Collections.singletonList(new UnitAccess(units.get(0), off, lim));
        assertHasUnits(expectedUnits, range);
    }

    /**************************************************************************************************/

    @Test
    public void testSubrange_MultipleUnits_Full() {
        long len1 = 256, len2 = 64, len3 = 192;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(0);
        assertEquals(len1 + len2 + len3, range.length());

        List<UnitAccess> expectedUnits = Arrays.asList(
                new UnitAccess(units.get(0), 0, len1),
                new UnitAccess(units.get(1), 0, len2),
                new UnitAccess(units.get(2), 0, len3));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_MultipleUnits_WithOffset() {
        long len1 = 256, len2 = 64, len3 = 192;
        long off = 32;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(off);
        assertEquals(len1 - off + len2 + len3, range.length());

        List<UnitAccess> expectedUnits = Arrays.asList(
                new UnitAccess(units.get(0), off, len1),
                new UnitAccess(units.get(1), 0, len2),
                new UnitAccess(units.get(2), 0, len3));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_MultipleUnits_WithOffsetInSecondUnit() {
        long len1 = 256, len2 = 64, len3 = 192;
        long off = 32;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(len1 + off);
        assertEquals(len2 - off + len3, range.length());

        List<UnitAccess> expectedUnits = Arrays.asList(
                new UnitAccess(units.get(1), off, len2),
                new UnitAccess(units.get(2), 0, len3));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_MultipleUnits_WithLength() {
        long len1 = 256, len2 = 64, len3 = 192;
        long lim = 64;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(0, len1 + len2 + lim);
        assertEquals(len1 + len2 + lim, range.length());

        List<UnitAccess> expectedUnits = Arrays.asList(
                new UnitAccess(units.get(0), 0, len1),
                new UnitAccess(units.get(1), 0, len2),
                new UnitAccess(units.get(2), 0, lim));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_MultipleUnits_WithLength_TrimLastUnit() {
        long len1 = 256, len2 = 64, len3 = 192;
        long lim = 64;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(0, len1 + lim);
        assertEquals(len1 + lim, range.length());

        List<UnitAccess> expectedUnits = Arrays.asList(
                new UnitAccess(units.get(0), 0, len1),
                new UnitAccess(units.get(1), 0, lim));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_MultipleUnits_WithOffsetAndLength() {
        long len1 = 256, len2 = 64, len3 = 192;
        long off = 192;
        long lim = 64;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(off, len1 - off + len2 + lim);
        assertEquals(len1 - off + len2 + lim, range.length());

        List<UnitAccess> expectedUnits = Arrays.asList(
                new UnitAccess(units.get(0), off, len1),
                new UnitAccess(units.get(1), 0, len2),
                new UnitAccess(units.get(2), 0, lim));
        assertHasUnits(expectedUnits, range);
    }

    @Test
    public void testSubrange_MultipleUnits_WithOffsetAndLength_TrimFirstAndLastUnits() {
        long len1 = 256, len2 = 64, len3 = 192;
        long off = 32;
        List<StorageUnit> units = mockStorageUnits(len1, len2, len3);
        DataRange range = new ReadWriteDataRange(units, 0, len3).getSubrange(len1 + off, 1);
        assertEquals(1, range.length());

        List<UnitAccess> expectedUnits = Collections.singletonList(new UnitAccess(units.get(1), off, off + 1));
        assertHasUnits(expectedUnits, range);
    }
}
