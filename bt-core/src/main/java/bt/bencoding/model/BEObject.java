package bt.bencoding.model;

import bt.bencoding.BEType;

public interface BEObject<T> {

    BEType getType();
    byte[] getContent();
    T getValue();
}
