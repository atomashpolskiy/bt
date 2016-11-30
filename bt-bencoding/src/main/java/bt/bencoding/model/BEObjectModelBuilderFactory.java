package bt.bencoding.model;

public interface BEObjectModelBuilderFactory {

    <T> BEObjectModelBuilder<T> getOrCreateBuilder(Class<T> sourceType);
}
