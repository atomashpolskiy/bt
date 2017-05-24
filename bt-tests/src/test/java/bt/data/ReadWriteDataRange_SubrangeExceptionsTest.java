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
