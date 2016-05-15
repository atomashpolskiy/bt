package bt.bencoding;

class BEStringBuilder implements BEObjectBuilder<String> {

    private static final char DELIMITER = ':';
    private StringBuilder buf;
    private int length;
    private int charsAcceptedCount;
    private boolean shouldReadBody;

    BEStringBuilder() {
        buf = new StringBuilder();
    }

    @Override
    public boolean accept(char c) {

        if (shouldReadBody) {
            if (charsAcceptedCount + 1 > length) {
                return false;
            }
        } else {
            if (charsAcceptedCount == 0 && (c == '0' || !Character.isDigit(c))) {
                throw new IllegalArgumentException("Unexpected token while reading string's length: " + c);
            }
            if (c == DELIMITER) {
                shouldReadBody = true;
                charsAcceptedCount = 0;
                length = Integer.parseInt(buf.toString());
                buf = new StringBuilder(length);
                return true;
            }
        }

        buf.append(c);
        charsAcceptedCount++;
        return true;
    }

    @Override
    public boolean acceptEOF() {
        return false;
    }

    @Override
    public String build() {
        if (!shouldReadBody) {
            throw new IllegalStateException("Can't build string: no content");
        }
        if (charsAcceptedCount < length) {
            throw new IllegalStateException("Can't build string: insufficient content");
        }
        return buf.toString();
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }
}
