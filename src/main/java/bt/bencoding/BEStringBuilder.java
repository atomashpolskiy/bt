package bt.bencoding;

import bt.bencoding.model.BEString;

import java.io.ByteArrayOutputStream;

class BEStringBuilder implements BEObjectBuilder<BEString> {

    static final char DELIMITER = ':';

    private ByteArrayOutputStream buf;
    private int length;
    private int bytesAcceptedCount;
    private boolean shouldReadBody;

    BEStringBuilder() {
        buf = new ByteArrayOutputStream();
    }

    @Override
    public boolean accept(int b) {

        char c = (char) b;
        if (shouldReadBody) {
            if (bytesAcceptedCount + 1 > length) {
                return false;
            }
        } else {
            if (bytesAcceptedCount == 0 && (c == '0' || !Character.isDigit(c))) {
                throw new IllegalArgumentException(
                        "Unexpected token while reading string's length (as ASCII char): " + c);
            }
            if (c == DELIMITER) {
                shouldReadBody = true;
                bytesAcceptedCount = 0;
                length = Integer.parseInt(buf.toString());
                buf = new ByteArrayOutputStream(length);
                return true;
            }
        }

        buf.write(b);
        bytesAcceptedCount++;
        return true;
    }

    @Override
    public BEString build() {
        if (!shouldReadBody) {
            throw new IllegalStateException("Can't build string: no content");
        }
        if (bytesAcceptedCount < length) {
            throw new IllegalStateException("Can't build string: insufficient content");
        }
        return new BEString(buf.toByteArray());
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }
}
