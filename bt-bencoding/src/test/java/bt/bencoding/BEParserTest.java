/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.bencoding;

import bt.bencoding.model.BEObject;
import bt.bencoding.serializers.BEParser;
import bt.bencoding.serializers.BtParseException;
import bt.bencoding.types.BEList;
import bt.bencoding.types.BEString;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests to make sure that BEncoding parsers work as intended.
 */
public class BEParserTest {

    private final Charset charset = StandardCharsets.UTF_8;

    @Test
    public void testParse_String1() {
        BEParser parser = new BEParser("1:s".getBytes());
        assertEquals(BEType.STRING, parser.readType());
        assertEquals("s", parser.readString().getValueAsString());
    }

    @Test
    public void testParse_String2() {
        BEParser parser = new BEParser("11:!@#$%^&*()_".getBytes());
        assertEquals(BEType.STRING, parser.readType());
        assertEquals("!@#$%^&*()_", parser.readString().getValueAsString());
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_EmptyString() {
        new BEParser("".getBytes());
    }

    @Test
    public void testParse_String_LengthStartsWithZero() {
        BEString string = new BEParser("0:".getBytes()).readString();
        assertNotNull(string);
        assertEquals(0, string.getValue().length);
    }

    @Test(expected = Exception.class)
    public void testParse_String_LengthStartsWithTwoZeros() {
        new BEParser("00:".getBytes()).readString();
    }

    @Test(expected = Exception.class)
    public void testParse_String_LengthLeadingZero() {
        new BEParser("01:t".getBytes()).readString();
    }

    @Test(expected = Exception.class)
    public void testParse_StringOnlyDelimiter() {
        new BEParser(":".getBytes()).readString();
    }

    @Test(expected = Exception.class)
    public void testStringLengthOverflow() {
        new BEParser((Long.toString(100_000_000_000L) + ":").getBytes()).readString();
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_InsufficientContent() {
        new BEParser("7:abcdef".getBytes()).readString();
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_NoContent() {
        new BEParser("1:".getBytes()).readString();
    }

    @Test(expected = Exception.class)
    public void testParse_String_Exception_NoDelimiter() {
        new BEParser("0".getBytes()).readString();
    }

    /**
     * Tests that parsing a utf8 BEncoded string works properly
     */
    @Test
    public void testParse_Utf8String() {
        byte[] utf8CharBytes = new byte[]{(byte) 0xE2, (byte) 0x84, (byte) 0xB5};
        byte[] bytes = new byte[2 + utf8CharBytes.length];
        bytes[0] = (byte) ('0' + utf8CharBytes.length);
        bytes[1] = ':';
        System.arraycopy(utf8CharBytes, 0, bytes, 2, utf8CharBytes.length);
        BEString s = new BEParser(bytes).readString();
        String utf16Str = new String(utf8CharBytes, StandardCharsets.UTF_8);
        Assert.assertEquals(1, utf16Str.toCharArray().length);
        Assert.assertEquals(1, utf16Str.length());
        Assert.assertEquals(utf16Str, s.toString());
        Assert.assertArrayEquals(s.getValue(), utf8CharBytes);
    }

    @Test
    public void testParse_Integer1() {
        BEParser parser = new BEParser("i1e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(1L, parser.readInteger().getValue().longValue());
    }

    @Test
    public void testParse_Integer_Negative() {
        BEParser parser = new BEParser("i-1e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(-1, parser.readInteger().getValue());
    }

    @Test(expected = Exception.class)
    public void testParse_Integer_Exception_ZeroLength() {
        BEParser parser = new BEParser("ie".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger();
    }

    @Test
    public void testParse_overflows_long() {
        BigInteger longOverflow = BigInteger.valueOf(Long.MAX_VALUE);
        longOverflow = longOverflow.multiply(BigInteger.valueOf(100));
        BEParser parser = new BEParser(("i" + longOverflow + "e").getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        Assert.assertEquals(longOverflow, parser.readInteger().getValue());
    }

    @Test
    public void testParse_negative_overflows_long() {
        BigInteger longOverflow = BigInteger.valueOf(Long.MAX_VALUE);
        longOverflow = longOverflow.multiply(BigInteger.valueOf(-100));
        BEParser parser = new BEParser(("i" + longOverflow + "e").getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        Assert.assertEquals(longOverflow, parser.readInteger().getValue());
    }

    @Test(expected = BtParseException.class)
    public void testParse_Integer_Exception_NegativeZero() {
        BEParser parser = new BEParser("i-0e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger().getValue();
    }

    @Test(expected = BtParseException.class)
    public void testParse_Integer_Exception_LeadingZeroWithNumber() {
        BEParser parser = new BEParser("i-01e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger().getValue();
    }

    @Test(expected = BtParseException.class)
    public void testParse_Integer_Exception_OnlyMultipleZeros() {
        BEParser parser = new BEParser("i00e".getBytes());
        assertEquals(BEType.INTEGER, parser.readType());
        parser.readInteger().getValue();
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
                new Object[]{"spam".getBytes(charset), "eggs".getBytes(charset), 1},
                parser.readList().getValue().stream()
                        .map(o -> ((BEObject) o).getValue())
                        .toArray()
        );
    }

    @Test
    public void testParse_Map1() {
        BEParser parser = new BEParser("d4:spaml1:a1:bee".getBytes());
        assertEquals(BEType.MAP, parser.readType());

        byte[][] expected = new byte[][]{"a".getBytes(charset), "b".getBytes(charset)};

        Map<String, BEObject<?>> map = parser.readMap().getValue();

        Object o = map.get("spam");
        assertNotNull(o);
        assertTrue(o instanceof BEList);

        BEList actual = (BEList) o;
        assertArrayEquals(expected,
                actual.getValue().stream()
                        .map(s -> ((BEString) s).getValue())
                        .toArray());
    }
}
