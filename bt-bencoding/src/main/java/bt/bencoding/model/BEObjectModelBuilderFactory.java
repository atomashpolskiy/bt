package bt.bencoding.model;

/**
 * Factory of model builders.
 *
 * @since 1.0
 */
public interface BEObjectModelBuilderFactory {

    /**
     * Create a model builder.
     *
     * @param sourceType Model source type.
     * @param <T> Java type of the model's definition object.
     * @return Model builder
     * @since 1.0
     */
    <T> BEObjectModelBuilder<T> getOrCreateBuilder(Class<T> sourceType);
}
