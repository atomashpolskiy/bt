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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * BEncoding encoder.
 *
 * @since 1.0
 */
public class BEEncoder {

    private static final Charset defaultCharset = Charset.forName("UTF-8");
    private static final BEEncoder instance = new BEEncoder();

    /**
     * Get default encoder.
     *
     * @since 1.0
     */
    public static BEEncoder encoder() {
        return instance;
    }

    /**
     * Write bencoded string to a binary output.
     *
     * @since 1.0
     */
    public void encode(BEString string, OutputStream out) throws IOException {

        Objects.requireNonNull(string);

        byte[] bytes = string.getValue();
        encodeString(bytes, out);
    }

    private void encodeString(byte[] bytes, OutputStream out) throws IOException {
        write(out, Integer.toString(bytes.length).getBytes(defaultCharset));
        write(out, ':');
        write(out, bytes);
    }

    /**
     * Write bencoded integer to a binary output.
     *
     * @since 1.0
     */
    public void encode(BEInteger integer, OutputStream out) throws IOException {

        Objects.requireNonNull(integer);

        BigInteger value = integer.getValue();
        write(out, BEParser.INTEGER_PREFIX);
        write(out, Integer.toString(value.intValueExact()).getBytes(defaultCharset));
        write(out, BEParser.EOF);
    }

    /**
     * Write bencoded list to a binary output.
     *
     * @since 1.0
     */
    public void encode(BEList list, OutputStream out) throws IOException {

        Objects.requireNonNull(list);

        List<? extends BEObject<?>> values = list.getValue();
        write(out, BEParser.LIST_PREFIX);

        for (BEObject<?> value : values) {
            value.writeTo(out);
        }

        write(out, BEParser.EOF);
    }

    /**
     * Write bencoded dictionary to a binary output.
     *
     * @since 1.0
     */
    public void encode(BEMap map, OutputStream out) throws IOException {
        Objects.requireNonNull(map);

        write(out, BEParser.MAP_PREFIX);

        TreeMap<byte[], BEObject<?>> values = map.getValue().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().getBytes(defaultCharset),
                        Map.Entry::getValue,
                        (BinaryOperator<BEObject<?>>) (u, u2) -> {
                            throw new IllegalStateException();
                        },
                        () -> new TreeMap<>(ByteStringComparator.comparator())));

        for (Map.Entry<byte[], BEObject<?>> e : values.entrySet()) {
            encodeString(e.getKey(), out);
            e.getValue().writeTo(out);
        }

        write(out, BEParser.EOF);
    }

    private void write(OutputStream out, int i) throws IOException {
        out.write(i);
    }

    private void write(OutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
    }
}
