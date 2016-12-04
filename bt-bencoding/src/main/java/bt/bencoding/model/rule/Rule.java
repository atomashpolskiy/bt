package bt.bencoding.model.rule;

/**
 * Validation primitive. Checks some condition on a given object.
 *
 * @since 1.0
 */
public interface Rule {

    /**
     * Validate a given object.
     *
     * @param object Arbitrary object
     * @return true if the object satisfies this rule's condition.
     * @since 1.0
     */
    boolean validate(Object object);

    /**
     * @return Human-readable description of this rule.
     * @since 1.0
     */
    String getDescription();
}
