package bt.bencoding.model;

import bt.bencoding.BEType;

import java.io.OutputStream;

public interface BEObject<T> {

    BEType getType();
    byte[] getContent();
    T getValue();
    void writeTo(OutputStream out);
}
