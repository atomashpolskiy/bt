package bt.bencoding.model;

import java.util.function.Function;

/**
 * Builds an object model from a Java-type based definition.
 *
 * @param <T> Java type of the model's definition object.
 * @since 1.0
 */
public interface BEObjectModelBuilder<T> extends Function<Object, BEObjectModel> {

    @Override
    default BEObjectModel apply(Object o) {

        if (o == null) {
            throw new NullPointerException("Missing source object -- null");
        }
        if (!getSourceType().isAssignableFrom(o.getClass())) {
            throw new IllegalStateException("Unexpected source type: " + o.getClass().getName());
        }

        @SuppressWarnings("unchecked")
        T t = (T) o;
        return buildModel(t);
    }

    /**
     * @return Model source type
     * @since 1.0
     */
    Class<T> getSourceType();

    /**
     * Build an object model from the provided model definition.
     *
     * @param t Model definition
     * @return Object model
     * @since 1.0
     */
    BEObjectModel buildModel(T t);
}
