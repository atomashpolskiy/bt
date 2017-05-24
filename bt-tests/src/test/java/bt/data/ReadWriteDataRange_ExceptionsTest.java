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
