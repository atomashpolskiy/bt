package bt.bencoding;

abstract class BEPrefixedTypeBuilder<T> implements BEObjectBuilder<T> {

    private boolean receivedPrefix;
    private boolean receivedEOF;

    @Override
    public final boolean accept(char c) {

        if (receivedEOF) {
            return false;
        }

        if (!receivedPrefix) {
            BEType type = getType();
            if (c == BEParser.getPrefixForType(type)) {
                receivedPrefix = true;
                return true;
            } else {
                throw new IllegalArgumentException("Invalid prefix for type " + type.name().toLowerCase() + ": " + c);
            }
        }

        if (c == BEParser.EOF && acceptEOF()) {
            receivedEOF = true;
            return true;
        }

        return doAccept(c);
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

    protected abstract boolean doAccept(char c);
    protected abstract T doBuild();
}
