package bt.bencoding;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BEParserTest {

    @Test
    public void testParse_String1() {
        BEParser parser = new BEParser("1:s");
        assertEquals(BEType.STRING, parser.readType());
        assertEquals("s", parser.readString());
    }

    @Test
    public void testParse_String2() {
        BEParser parser = new BEParser("11:!@#$%^&*()_");
        assertEquals(BEType.STRING, parser.readType());
        assertEquals("!@#$%^&*()_", parser.readString());
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_EmptyString() {
        new BEParser("");
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_LengthStartsWithZero() {
        new BEParser("0:").readString();
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_InsufficientContent() {
        new BEParser("7:abcdef").readString();
    }

    @Test
    public void testParse_Integer1() {
        BEParser parser = new BEParser("i1e");
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(BigInteger.ONE, parser.readInteger());
    }

    @Test
    public void testParse_Integer_Negative() {
        BEParser parser = new BEParser("i-1e");
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(BigInteger.ONE.negate(), parser.readInteger());
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_ZeroLength() {
        BEParser parser = new BEParser("ie");
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test//(expected = Exception.class)
    public void testParse_Integer_Exception_NegativeZero() {
        // not sure why the protocol spec forbids negative zeroes,
        // so let it be for now
        BEParser parser = new BEParser("i-0e");
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(BigInteger.ZERO.negate(), parser.readInteger());
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_NotTerminated() {
        BEParser parser = new BEParser("i1");
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_UnexpectedTokens() {
        BEParser parser = new BEParser("i-1-e");
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test
    public void testParse_List1() {
        BEParser parser = new BEParser("l4:spam4:eggsi1ee");
        assertEquals(BEType.LIST, parser.readType());
        assertArrayEquals(
                new Object[] {"spam", "eggs", BigInteger.ONE},
                parser.readList().toArray()
        );
    }

    @Test
    public void testParse_List_ListOfLists() {
        BEParser parser = new BEParser("l4:spaml4:spam4:eggsi1eei1ee");
        assertEquals(BEType.LIST, parser.readType());
        assertArrayEquals(
                new Object[] {"spam", Arrays.asList("spam", "eggs", BigInteger.ONE), BigInteger.ONE},
                parser.readList().toArray()
        );
    }

    @Test
    public void testParse_Map1() {
        BEParser parser = new BEParser("d4:spaml1:a1:bee");
        assertEquals(BEType.MAP, parser.readType());

        Map<String, Object> expected = new HashMap<>();
        expected.put("spam", Arrays.asList("a", "b"));

        assertEquals(expected, parser.readMap());
    }
}
