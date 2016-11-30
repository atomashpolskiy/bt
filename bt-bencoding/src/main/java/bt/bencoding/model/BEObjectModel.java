package bt.bencoding.model;

import bt.bencoding.BEType;

public interface BEObjectModel {

    BEType getType();
    ValidationResult validate(Object object);
}
