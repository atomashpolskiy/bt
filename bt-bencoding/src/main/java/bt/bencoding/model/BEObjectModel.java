package bt.bencoding.model;

import bt.bencoding.BEType;

/**
 * Object model.
 *
 * @since 1.0
 */
public interface BEObjectModel {

    /**
     * @return BEncoding type of the objects that this model can be applied to.
     * @since 1.0
     */
    BEType getType();

    /**
     * Validate a given object.
     *
     * @param object Object of this model's BEncoding type.
     * @return Validation result (failed, if this model cannot be applied to the {@code object}'s type).
     * @since 1.0
     */
    ValidationResult validate(Object object);
}
