package bt.bencoding;

import bt.bencoding.model.BEObject;

import java.io.ByteArrayOutputStream;

abstract class BEPrefixedTypeBuilder<T extends BEObject> implements BEObjectBuilder<T> {

    private ByteArrayOutputStream buf;
    private boolean receivedPrefix;
    private boolean receivedEOF;

    BEPrefixedTypeBuilder() {
        buf = new ByteArrayOutputStream();
    }

    @Override
    public final boolean accept(int b) {
        return accept(b, true);
    }

    // work-around for duplicate logging of received bytes
    // when BEPrefixTypeBuilder.accept(int) is called by itself
    // -- descendants should use this method instead
    protected boolean accept(int b, boolean shouldLog) {

        if (receivedEOF) {
            return false;
        }

        if (shouldLog) {
            buf.write(b);
        }

        if (!receivedPrefix) {
            BEType type = getType();
            if (b == BEParser.getPrefixForType(type)) {
                receivedPrefix = true;
                return true;
            } else {
                throw new IllegalArgumentException("Invalid prefix for type " + type.name().toLowerCase()
                        + " (as ASCII char): " + (char) b);
            }
        }

        if (b == BEParser.EOF && acceptEOF()) {
            receivedEOF = true;
            return true;
        }

        return doAccept(b);
    }

    @Override
    public T build() {

        if (!receivedPrefix) {
            throw new IllegalStateException("Can't build " + getType().name().toLowerCase() + " -- no content");
        }
        if (!receivedEOF) {
            throw new IllegalStateException("Can't build " + getType().name().toLowerCase() + " -- content was not terminated");
        }
        return doBuild(buf.toByteArray());
    }

    protected abstract boolean doAccept(int b);
    protected abstract T doBuild(byte[] content);
    protected abstract boolean acceptEOF();
}
