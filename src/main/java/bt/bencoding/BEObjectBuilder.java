package bt.bencoding;

interface BEObjectBuilder<T> {
    boolean accept(int b);
    T build();
    BEType getType();
}
