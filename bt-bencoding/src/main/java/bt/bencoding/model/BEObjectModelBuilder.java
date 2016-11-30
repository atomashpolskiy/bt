package bt.bencoding.model;

import java.util.function.Function;

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

    Class<T> getSourceType();

    BEObjectModel buildModel(T t);
}
