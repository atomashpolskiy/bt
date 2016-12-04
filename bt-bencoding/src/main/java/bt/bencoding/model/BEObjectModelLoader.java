package bt.bencoding.model;

import java.io.InputStream;

/**
 * Loads model definitions.
 *
 * @since 1.0
 */
public interface BEObjectModelLoader {

    /**
     * Load model definition from a given source.
     * @param source Source. Must be closed by the caller.
     * @return Model definition
     * @since 1.0
     */
    BEObjectModel load(InputStream source);
}
