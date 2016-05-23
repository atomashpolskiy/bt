package bt.bencoding;

import bt.BtException;
import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEList;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;

import java.io.IOException;
import java.net.URL;

public class BEParser implements AutoCloseable {

    static final char EOF = 'e';

    static final char INTEGER_PREFIX = 'i';
    static final char LIST_PREFIX = 'l';
    static final char MAP_PREFIX = 'd';

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

    public BEString readString() {
        return readObject(BEType.STRING, BEStringBuilder.class);
    }

    public BEInteger readInteger() {
        return readObject(BEType.INTEGER, BEIntegerBuilder.class);
    }

    public BEList readList() {
        return readObject(BEType.LIST, BEListBuilder.class);
    }

    public BEMap readMap() {
        return readObject(BEType.MAP, BEMapBuilder.class);
    }

    private <T extends BEObject> T readObject(BEType type, Class<? extends BEObjectBuilder<T>> builderClass) {

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
