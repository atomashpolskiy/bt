package bt.data.range;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteRangeTest {

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

    @Test
    public void testByteRange_Modification() {
        byte[] bytes1 = new byte[]{0,1,2,3,4,5,6,7,8,9};
        ByteRange range = new ByteRange(bytes1);

        byte[] bytes2 = new byte[]{9,8,7,6,5,4,3,2,1,0};
        range.putBytes(bytes2);
        assertEquals(10, range.length());
        assertArrayEquals(bytes2, range.getBytes());

        byte[] bytes3 = new byte[]{20,21,22,23,24};
        range.putBytes(bytes3);
        assertEquals(10, range.length());
        assertArrayEquals(new byte[]{/*bytes3*/20,21,22,23,24,/*bytes2*/4,3,2,1,0}, range.getBytes());
    }
}
