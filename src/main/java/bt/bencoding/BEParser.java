package bt.bencoding;

import bt.BtException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public class BEParser implements AutoCloseable {

    static final char EOF = 'e';

    private static final char INTEGER_PREFIX = 'i';
    private static final char LIST_PREFIX = 'l';
    private static final char MAP_PREFIX = 'd';

    private Scanner scanner;
    private final BEType type;
    private Object parsedObject;

    public BEParser(URL url) {

        if (url == null) {
            throw new NullPointerException("Missing URL");
        }
        try {
            this.scanner = new Scanner(url.openStream());
        } catch (IOException e) {
            throw new BtException("Failed to open stream for URL: " + url, e);
        }
        this.type = getTypeForPrefix((char) scanner.peek());
    }

    public BEParser(byte[] bs) {

        if (bs == null || bs.length == 0) {
            throw new IllegalArgumentException("Can't parse bytes array: null or empty");
        }
        this.scanner = new Scanner(bs);
        this.type = getTypeForPrefix((char) scanner.peek());
    }

    public BEType readType() {
        return type;
    }

    static char getPrefixForType(BEType type) {

        if (type == null) {
            throw new NullPointerException("Can't get prefix -- type is null");
        }

        switch (type) {
            case INTEGER:
                return INTEGER_PREFIX;
            case LIST:
                return LIST_PREFIX;
            case MAP:
                return MAP_PREFIX;
            default:
                throw new IllegalArgumentException("Unknown type: " + type.name().toLowerCase());
        }
    }

    static BEType getTypeForPrefix(char c) {
        if (Character.isDigit(c)) {
            return BEType.STRING;
        }
        switch (c) {
            case INTEGER_PREFIX: {
                return BEType.INTEGER;
            }
            case LIST_PREFIX: {
                return BEType.LIST;
            }
            case MAP_PREFIX: {
                return BEType.MAP;
            }
            default: {
                throw new IllegalStateException("Invalid type prefix: " + c);
            }
        }
    }

    static BEObjectBuilder<?> builderForType(BEType type) {
        switch (type) {
            case STRING: {
                return new BEStringBuilder();
            }
            case INTEGER: {
                return new BEIntegerBuilder();
            }
            case LIST: {
                return new BEListBuilder();
            }
            case MAP: {
                return new BEMapBuilder();
            }
            default: {
                throw new IllegalStateException("Unknown type: " + type.name().toLowerCase());
            }
        }
    }

    public String readString(Charset charset) {
        byte[] bytes = readObject(BEType.STRING, BEStringBuilder.class);
        return new String(bytes, charset);
    }

    public BigInteger readInteger() {
        return readObject(BEType.INTEGER, BEIntegerBuilder.class);
    }

    public List<Object> readList() {
        return readObject(BEType.LIST, BEListBuilder.class);
    }

    public Map<String, Object> readMap() {
        return readObject(BEType.MAP, BEMapBuilder.class);
    }

    private <T> T readObject(BEType type, Class<? extends BEObjectBuilder<T>> builderClass) {

        assertType(type);

        @SuppressWarnings("unchecked")
        T result = (T) parsedObject;
        if (result == null) {
            try {
                // relying on the default constructor being present
                parsedObject = result = scanner.readObject(builderClass.newInstance());
            } catch (Exception e) {
                throw new BtParseException("Failed to read from encoded data", scanner.getScannedContents(), e);
            }
        }
        return result;
    }

    private void assertType(BEType type) {

        if (type == null) {
            throw new NullPointerException("Invalid type -- null");
        }

        if (this.type != type) {
            throw new IllegalStateException(
                    "Can't read " + type.name().toLowerCase() + " from: " + this.type.name().toLowerCase());
        }
    }

    @Override
    public void close() {
        scanner.close();
    }
}
