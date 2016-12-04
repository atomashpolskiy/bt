package bt.bencoding;

import bt.bencoding.model.BEObject;

interface BEObjectBuilder<T extends BEObject> {

    boolean accept(int b);

    T build();

    BEType getType();
}
