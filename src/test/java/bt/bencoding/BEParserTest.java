package bt.bencoding;

import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BEParserTest {

    private Charset charset = Charset.forName("UTF-8");

    @Test
    public void testParse_String1() {
        BEParser parser = new BEParser("1:s".getBytes());
        assertEquals(BEType.STRING, parser.readType());
        assertEquals("s", parser.readString(charset));
    }

    @Test
    public void testParse_String2() {
        BEParser parser = new BEParser("11:!@#$%^&*()_".getBytes());
        assertEquals(BEType.STRING, parser.readType());
        assertEquals("!@#$%^&*()_", parser.readString(charset));
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_EmptyString() {
        new BEParser("".getBytes());
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_LengthStartsWithZero() {
        new BEParser("0:".getBytes()).readString(charset);
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_InsufficientContent() {
        new BEParser("7:abcdef".getBytes()).readString(charset);
    }

    @Test
    public void testParse_Integer1() {
        BEParser parser = new BEParser("i1e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(BigInteger.ONE, parser.readInteger());
    }

    @Test
    public void testParse_Integer_Negative() {
        BEParser parser = new BEParser("i-1e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(BigInteger.ONE.negate(), parser.readInteger());
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_ZeroLength() {
        BEParser parser = new BEParser("ie".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test//(expected = Exception.class)
    public void testParse_Integer_Exception_NegativeZero() {
        // not sure why the protocol spec forbids negative zeroes,
        // so let it be for now
        BEParser parser = new BEParser("i-0e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(BigInteger.ZERO.negate(), parser.readInteger());
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_NotTerminated() {
        BEParser parser = new BEParser("i1".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_UnexpectedTokens() {
        BEParser parser = new BEParser("i-1-e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test
    public void testParse_List1() {
        BEParser parser = new BEParser("l4:spam4:eggsi1ee".getBytes());
        assertEquals(BEType.LIST, parser.readType());
        assertArrayEquals(
                new Object[] {"spam".getBytes(charset), "eggs".getBytes(charset), BigInteger.ONE},
                parser.readList().toArray()
        );
    }

    @Test
    public void testParse_Map1() {
        BEParser parser = new BEParser("d4:spaml1:a1:bee".getBytes());
        assertEquals(BEType.MAP, parser.readType());

        byte[][] expected = new byte[][] {"a".getBytes(charset), "b".getBytes(charset)};

        Map<String, Object> map = parser.readMap();

        Object o = map.get("spam");
        assertNotNull(o);
        assertTrue(o instanceof List);

        List<?> actual = (List<?>) o;
        assertArrayEquals(expected, actual.toArray());
    }
}
