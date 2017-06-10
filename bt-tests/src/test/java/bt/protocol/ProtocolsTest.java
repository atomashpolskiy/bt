package bt.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ProtocolsTest {

    private static final byte[] bytes = new byte[]{0x00, 0x0F, (byte) 0xF0, (byte) 0xFF};

    @Test
    public void test_toHex() {
        assertEquals("000ff0ff", Protocols.toHex(bytes));
    }

    @Test
    public void test_fromHex_LowerCase() {
        String s = "000ff0ff";
        assertArrayEquals(bytes, Protocols.fromHex(s));
    }

    @Test
    public void test_fromHex_UpperCase() {
        String s = "000FF0FF";
        assertArrayEquals(bytes, Protocols.fromHex(s));
    }

    @Test
    public void test_fromHex_MixedCase() {
        String s = "000fF0Ff";
        assertArrayEquals(bytes, Protocols.fromHex(s));
    }
}
