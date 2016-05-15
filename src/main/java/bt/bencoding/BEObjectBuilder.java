package bt.bencoding;

interface BEObjectBuilder<T> {
    boolean accept(char c);
    boolean acceptEOF();
    T build();
    BEType getType();
}
