package bt.bencoding;

import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEList;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class BEEncoderTest {

    private static final Charset defaultCharset = Charset.forName("UTF-8");

    @Test
    public void testEncoder_String() {

        String s = "some string";

        BEParser parser = encodeAndCreateParser(new BEString(s.getBytes(defaultCharset)));
        assertEquals(BEType.STRING, parser.readType());
        assertEquals(s, parser.readString().getValue(defaultCharset));
    }

    @Test
    public void testEncoder_Integer() {

        BigInteger i = BigInteger.valueOf(1234567890);

        BEParser parser = encodeAndCreateParser(new BEInteger(null, i));
        assertEquals(BEType.INTEGER, parser.readType());
        assertEquals(i, parser.readInteger().getValue());
    }

    @Test
    public void testEncode_List() {

        List<BEObject<?>> l = new ArrayList<>();
        l.add(new BEString("some string1:2#3".getBytes(defaultCharset)));
        l.add(new BEInteger(null, BigInteger.valueOf(1234567890)));
        l.add(new BEMap(null, new HashMap<>()));

        BEParser parser = encodeAndCreateParser(new BEList(null, l));
        assertEquals(BEType.LIST, parser.readType());
        assertEquals(l, parser.readList().getValue());
    }

    @Test
    public void testEncode_Map() {

        BEString s = new BEString("some string1:2#3".getBytes(defaultCharset));
        BEInteger i = new BEInteger(null, BigInteger.valueOf(1234567890));
        BEMap emptyMap = new BEMap(null, new HashMap<>());

        BEList l = new BEList(null, Arrays.asList(s, i, emptyMap));

        Map<String, BEObject<?>> m = new HashMap<>();
        m.put("4:list", l);
        m.put("key1", s);
        m.put("key2", emptyMap);

        BEParser parser = encodeAndCreateParser(new BEMap(null, m));
        assertEquals(BEType.MAP, parser.readType());
        assertEquals(m, parser.readMap().getValue());
    }

    private static BEParser encodeAndCreateParser(BEObject<?> object) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        object.writeTo(out);
        return new BEParser(out.toByteArray());
    }
}
