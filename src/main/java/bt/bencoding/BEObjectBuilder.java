package bt.bencoding;

interface BEObjectBuilder<T> {
    boolean accept(int b);
    boolean acceptEOF();
    T build();
    BEType getType();
}
