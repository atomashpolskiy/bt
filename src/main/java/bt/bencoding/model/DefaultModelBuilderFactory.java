package bt.bencoding.model;

import bt.BtException;

import java.util.Map;

public class DefaultModelBuilderFactory implements BEObjectModelBuilderFactory {

    @SuppressWarnings("unchecked")
    @Override
    public <T> BEObjectModelBuilder<T> getOrCreateBuilder(Class<T> sourceType) {

        if (sourceType == null) {
            throw new NullPointerException("Missing source type -- null");
        }

        if (Map.class.isAssignableFrom(sourceType)) {
            return (BEObjectModelBuilder<T>) new JUMModelBuilder();
        }
        throw new BtException("No builders registered for type: " + sourceType.getName());
    }
}
