package bt.bencoding;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class BEParser {

    static final char EOF = 'e';

    private static final char INTEGER_PREFIX = 'i';
    private static final char LIST_PREFIX = 'l';
    private static final char MAP_PREFIX = 'd';

    private String encodedStr;
    private Scanner scanner;
    private final BEType type;
    private Object parsedObject;

    public BEParser(String encodedStr) {

        if (encodedStr == null || encodedStr.isEmpty()) {
            throw new IllegalArgumentException("Can't parse string: null or empty");
        }
        this.encodedStr = encodedStr;
        this.scanner = new Scanner(encodedStr);
        this.type = getTypeForPrefix(scanner.peek());
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

    public String readString() {
        return readObject(BEType.STRING, BEStringBuilder.class);
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
                throw new RuntimeException("Failed to read from encoded string: " + encodedStr, e);
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
}
