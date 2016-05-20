package bt.bencoding;

abstract class BEPrefixedTypeBuilder<T> implements BEObjectBuilder<T> {

    private boolean receivedPrefix;
    private boolean receivedEOF;

    @Override
    public final boolean accept(int b) {

        if (receivedEOF) {
            return false;
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
        return doBuild();
    }

    protected abstract boolean doAccept(int b);
    protected abstract T doBuild();
    protected abstract boolean acceptEOF();
}
