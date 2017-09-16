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

package bt.bencoding;

import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEList;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        try {
            object.writeTo(out);
        } catch (IOException e) {
            // can't happen
        }
        return new BEParser(out.toByteArray());
    }
}
