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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BEEncoder {

    private static final Charset defaultCharset = Charset.forName("UTF-8");
    private static final BEEncoder instance = new BEEncoder();

    public static BEEncoder encoder() {
        return instance;
    }

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

    public void encode(BEInteger integer, OutputStream out) throws IOException {

        Objects.requireNonNull(integer);

        BigInteger value = integer.getValue();
        write(out, BEParser.INTEGER_PREFIX);
        write(out, Integer.toString(value.intValueExact()).getBytes(defaultCharset));
        write(out, BEParser.EOF);
    }

    public void encode(BEList list, OutputStream out) throws IOException {

        Objects.requireNonNull(list);

        List<? extends BEObject<?>> values = list.getValue();
        write(out, BEParser.LIST_PREFIX);

        for (BEObject<?> value : values) {
            value.writeTo(out);
        }

        write(out, BEParser.EOF);
    }

    public void encode(BEMap map, OutputStream out) throws IOException {

        Objects.requireNonNull(map);

        Map<String, BEObject<?>> values = map.getValue();
        write(out, BEParser.MAP_PREFIX);

        List<String> keys = new ArrayList<>(values.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            encodeString(key.getBytes(defaultCharset), out);
            values.get(key).writeTo(out);
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
